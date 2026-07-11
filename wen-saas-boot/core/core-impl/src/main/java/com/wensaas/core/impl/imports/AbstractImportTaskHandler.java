package com.wensaas.core.impl.imports;

import com.wensaas.core.api.audit.AuditEvent;
import com.wensaas.core.api.audit.AuditLogRecorder;
import com.wensaas.core.api.audit.AuditObjects;
import com.wensaas.core.api.file.FileService;
import com.wensaas.core.api.file.FileUploadRequest;
import com.wensaas.core.api.file.FileVisibility;
import com.wensaas.core.api.imports.ImportErrorCode;
import com.wensaas.core.api.imports.ImportValidationResolver;
import com.wensaas.core.api.imports.PreviewRow;
import com.wensaas.core.api.imports.RowError;
import com.wensaas.core.api.request.RequestContext;
import com.wensaas.core.api.spi.def.ImportEntityDef;
import com.wensaas.core.api.task.TaskContext;
import com.wensaas.core.api.task.TaskHandler;
import com.wensaas.core.impl.spi.registry.ImportEntityRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 数据导入执行处理器基类。
 *
 * <p>按 {@code params.fileId} 重读原文件 + 重校验（Redis 会话不存全量行），逐行交子类写库；
 * 重校验也会抓住 parse→confirm 间新增的并发冲突。失败行汇总成「结果-失败明细.xlsx」落文件中心，
 * 写入 {@code result_data.fileId}，前端经任务中心 {@code /api/v1/tasks/{id}/download} 取。
 *
 * <p>handler 是单例 bean，**不得用实例字段持有单次执行的状态**（不同租户可并发跑同一 handler）。
 * 每次执行的中间态（如 name→id 映射）经 {@link #prepare} 产出 {@code S}，再由 {@link #writeRow} 接收。
 *
 * @param <S> 单次执行的中间态类型（无中间态用 {@link Void}）
 */
public abstract class AbstractImportTaskHandler<S> implements TaskHandler {

    private static final Logger log = LoggerFactory.getLogger(AbstractImportTaskHandler.class);
    private static final String XLSX_MIME =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final ImportEntityRegistry entityRegistry;
    private final ImportValidationEngine engine;
    private final FileService fileService;
    private final Map<String, ImportValidationResolver> resolvers;
    /** 可空：audit 关闭时为 null，跳过导入审计。 */
    private final AuditLogRecorder auditRecorder;

    protected AbstractImportTaskHandler(ImportEntityRegistry entityRegistry,
                                        ImportValidationEngine engine,
                                        FileService fileService,
                                        List<ImportValidationResolver> resolverList,
                                        AuditLogRecorder auditRecorder) {
        this.entityRegistry = entityRegistry;
        this.engine = engine;
        this.fileService = fileService;
        this.resolvers = resolverList.stream()
                .collect(Collectors.toMap(ImportValidationResolver::entityCode, Function.identity()));
        this.auditRecorder = auditRecorder;
    }

    /** 导入实体编码（与所属 {@link ImportEntityDef#code()} 一致）。 */
    protected abstract String entityCode();

    /**
     * 执行前钩子（带引擎校验产物，可直接取 {@code outcome.referenceIds(fieldKey)} 的关联 id 映射）；
     * 默认回落到 {@link #prepare(List)}。
     */
    protected S prepare(List<PreviewRow> okRows, ImportValidationOutcome outcome) {
        return prepare(okRows);
    }

    /** 执行前钩子：产出单次执行的中间态（如 name→id 映射）；默认无（{@code null}）。 */
    protected S prepare(List<PreviewRow> okRows) {
        return null;
    }

    /** 写单行；失败抛 {@link RuntimeException}（被捕获记失败 + 收进结果明细）。 */
    protected abstract void writeRow(S state, Map<String, String> cells);

    @Override
    public void execute(TaskContext ctx) {
        AuditObjects.clear(); // 消费线程复用：清掉可能的上轮残留，保证本次 object_refs 干净
        String fileId = String.valueOf(ctx.params().get("fileId"));
        ImportEntityDef def = entityRegistry.findByKey(entityCode())
                .orElseThrow(() -> new IllegalStateException("导入实体未注册: " + entityCode()));

        byte[] bytes = fileService.readContent(fileId);
        ImportValidationOutcome outcome = engine.validate(def,
                ImportExcelSupport.readRows(def, new ByteArrayInputStream(bytes)), resolvers.get(entityCode()));
        List<PreviewRow> okRows = outcome.rows().stream().filter(PreviewRow::ok).toList();
        int total = okRows.size();
        ctx.setTotal(total); // 回写总条数：进度分母 + sys_task.total_count（否则前端进度无分母、DB total_count 为 null）
        S state = prepare(okRows, outcome);

        int success = 0;
        List<PreviewRow> failedRows = new ArrayList<>();
        for (int i = 0; i < okRows.size(); i++) {
            if (ctx.isCancelled()) {
                log.info("import.exec cancelled entity={} processed={}/{}", entityCode(), i, total);
                return;
            }
            PreviewRow row = okRows.get(i);
            try {
                writeRow(state, row.cells());
                success++;
                ctx.recordSuccess(1);
            } catch (RuntimeException ex) {
                ctx.recordFailure(1);
                failedRows.add(new PreviewRow(row.rowNo(), row.excelRow(), row.cells(), false,
                        List.of(new RowError(row.rowNo(), null, ImportErrorCode.BUSINESS_INVALID,
                                ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()))));
                log.warn("import.exec row failed entity={} rowNo={}", entityCode(), row.rowNo(), ex);
            }
            ctx.updateProgress(total == 0 ? 100 : (int) ((i + 1) * 100L / total), i + 1);
        }

        Map<String, Object> result = new java.util.HashMap<>();
        result.put("total", total);
        result.put("success", success);
        result.put("failed", failedRows.size());
        if (!failedRows.isEmpty()) {
            result.put("fileId", uploadResultReport(def, failedRows));
        }
        ctx.setResult(result);
        log.info("import.exec done entity={} total={} success={} failed={}",
                entityCode(), total, success, failedRows.size());

        recordImportAudit(ctx, def, total, success, failedRows.size());
    }

    /**
     * 记录一条 ② 导入审计（汇总：谁/导入了什么/计数）。
     *
     * <p>operator 经提交参数透传（TaskContext 无 operator）；requestId 由 {@code TaskExecutionConsumer} 还原；
     * object_refs 取 {@link AuditObjects#drain()}——子类如逐行 {@code AuditObjects.add} 即自动带上 1:N（当前内置导入未登记）。
     */
    private void recordImportAudit(TaskContext ctx, ImportEntityDef def, int total, int success, int failed) {
        if (auditRecorder == null) {
            return;
        }
        try {
            auditRecorder.record(new AuditEvent(
                    null, ctx.tenantId(), null, RequestContext.get(),
                    asLong(ctx.params().get("operatorId")), (String) ctx.params().get("operatorName"), null,
                    "import", "导入" + def.name(), def.name(),
                    null, "共 " + total + " · 成功 " + success + " · 失败 " + failed,
                    null, null, 200, null, null, null, null,
                    Instant.now(), AuditObjects.drain()));
        } catch (RuntimeException ex) {
            log.warn("import.audit.fail entity={} reason={}", entityCode(), ex.getMessage());
        }
    }

    private static Long asLong(Object v) {
        if (v instanceof Number n) {
            return n.longValue();
        }
        if (v instanceof String s && !s.isBlank()) {
            try {
                return Long.valueOf(s.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private String uploadResultReport(ImportEntityDef def, List<PreviewRow> failedRows) {
        byte[] xlsx = ImportExcelSupport.writeErrorReport(def, failedRows);
        FileUploadRequest req = new FileUploadRequest("导入结果-失败明细.xlsx", XLSX_MIME, xlsx.length,
                new ByteArrayInputStream(xlsx), FileVisibility.BUSINESS_ONLY, "import", "导入结果", null, null);
        return fileService.uploadDirect(req).fileId();
    }
}
