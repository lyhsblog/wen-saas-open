package com.wensaas.core.impl.exports;

import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.auth.user.UserPrincipal;
import com.wensaas.core.api.exports.ExportException;
import com.wensaas.core.api.exports.spi.ExportDataProvider;
import com.wensaas.core.api.file.FileService;
import com.wensaas.core.api.file.FileUploadRequest;
import com.wensaas.core.api.file.FileUploadResult;
import com.wensaas.core.api.file.FileVisibility;
import com.wensaas.core.api.notification.send.Channel;
import com.wensaas.core.api.notification.send.MessageCategory;
import com.wensaas.core.api.notification.send.MessageSendRequest;
import com.wensaas.core.api.notification.send.MessageService;
import com.wensaas.core.api.notification.send.Priority;
import com.wensaas.core.api.notification.send.RecipientSpec;
import com.wensaas.core.api.task.TaskContext;
import com.wensaas.core.api.task.TaskHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 业务数据导出执行处理器。任务参数携带导出对象 / 筛选条件 /
 * 申请人快照；执行时以<b>申请人</b>身份 {@code UserContext.runAs} 调 {@code ExportDataProvider}
 * 取数（已注册数据权限的表自动按申请人 data_scope 过滤），SXSSF 流式写 xlsx → 文件中心落库 →
 * {@code setResult(fileId,...)}（下载复用任务中心 {@code GET /api/v1/tasks/{id}/download}）。
 *
 * <p>终态通知自管（{@link #suppressTerminalNotification()}）：成功发
 * {@code sys.task.export.completed}（带任务中心跳转），失败发 {@code sys.task.failed}。
 */
public class ExportTaskHandler implements TaskHandler {

    public static final String HANDLER_CODE = "sys.export.generate";
    /** 单次导出硬上限行数；超出截断并在结果与通知中标注。 */
    public static final int MAX_ROWS = 100_000;

    private static final Logger log = LoggerFactory.getLogger(ExportTaskHandler.class);
    private static final String XLSX_MIME =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final DateTimeFormatter FILE_TS =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.of("Asia/Shanghai"));
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final Map<String, ExportDataProvider> providers;
    private final FileService fileService;
    private final ObjectProvider<MessageService> messageServiceProvider;

    public ExportTaskHandler(List<ExportDataProvider> providers,
                             FileService fileService,
                             ObjectProvider<MessageService> messageServiceProvider) {
        this.providers = providers.stream()
                .collect(Collectors.toMap(ExportDataProvider::objectCode, Function.identity(), (a, b) -> a));
        this.fileService = fileService;
        this.messageServiceProvider = messageServiceProvider;
    }

    @Override
    public String handlerCode() {
        return HANDLER_CODE;
    }

    /** 成功 / 失败通知均自发（成功带任务中心下载指引），抑制通用终态通知免重复。 */
    @Override
    public boolean suppressTerminalNotification() {
        return true;
    }

    @Override
    public void execute(TaskContext ctx) {
        Map<String, Object> p = ctx.params();
        String objectCode = str(p.get("objectCode"));
        String objectName = str(p.get("objectName"));
        long operatorId = Long.parseLong(str(p.get("operatorId")));
        String operatorName = str(p.get("operatorName"));
        boolean tenantOwner = Boolean.parseBoolean(str(p.get("operatorTenantOwner")));
        Long mainDeptId = p.get("operatorMainDeptId") == null
                ? null : Long.valueOf(str(p.get("operatorMainDeptId")));
        long estimated = p.get("estimatedCount") == null ? 0 : Long.parseLong(str(p.get("estimatedCount")));
        Map<String, String> filters = stringMap(p.get("filters"));

        ExportDataProvider provider = providers.get(objectCode);
        if (provider == null) {
            throw new ExportException(ExportException.Code.OBJECT_UNKNOWN,
                    "导出对象未注册 ExportDataProvider：" + objectCode);
        }

        UserPrincipal applicant = new UserPrincipal(operatorId, ctx.tenantId(), operatorName,
                "task:" + ctx.taskId(), tenantOwner, mainDeptId, false, null);
        try (ExportXlsxWriter writer = new ExportXlsxWriter(provider.columns(),
                ctx::isCancelled, MAX_ROWS)) {
            UserContext.runAs(applicant, () -> provider.export(filters,
                    new ProgressReportingSink(writer, ctx, estimated)));

            if (ctx.isCancelled()) {
                log.info("[export] 任务已取消 taskId={} object={} rows={}",
                        ctx.taskId(), objectCode, writer.rowsWritten());
                return;
            }

            long rows = writer.rowsWritten();
            byte[] xlsx = writer.finish();
            String fileName = String.format("导出-%s-%s.xlsx",
                    objectName == null || objectName.isBlank() ? objectCode : objectName,
                    FILE_TS.format(java.time.Instant.now()));
            FileUploadResult uploaded = fileService.uploadDirect(new FileUploadRequest(
                    fileName, XLSX_MIME, xlsx.length, new ByteArrayInputStream(xlsx),
                    FileVisibility.BUSINESS_ONLY, "sys-export", "业务数据导出", ctx.taskId(), null));

            ctx.recordSuccess((int) Math.min(rows, Integer.MAX_VALUE));
            ctx.updateProgress(100, (int) Math.min(rows, Integer.MAX_VALUE));
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("fileId", uploaded.fileId());
            result.put("fileName", fileName);
            result.put("rows", rows);
            result.put("truncated", writer.truncated());
            ctx.setResult(result);

            notifyCompleted(ctx, operatorId, objectName, rows, writer.truncated());
            log.info("[export] 导出完成 taskId={} object={} rows={} truncated={} bytes={}",
                    ctx.taskId(), objectCode, rows, writer.truncated(), xlsx.length);
        } catch (RuntimeException e) {
            log.error("[export] 导出失败 taskId={} object={} reason={}",
                    ctx.taskId(), objectCode, e.getMessage(), e);
            notifyFailed(ctx, operatorId, objectName, e.getMessage());
            throw e;
        }
    }

    /** 任务参数构造（直接发起与审批通过投递共用，保证 handler 解析口径一致）。 */
    public static Map<String, Object> buildParams(String objectCode, String objectName,
                                                  Map<String, String> filters, long estimatedCount,
                                                  long operatorId, String operatorName,
                                                  boolean operatorTenantOwner, Long operatorMainDeptId) {
        Map<String, Object> params = new HashMap<>();
        params.put("objectCode", objectCode);
        params.put("objectName", objectName);
        params.put("filters", filters == null ? Map.of() : filters);
        params.put("estimatedCount", estimatedCount);
        params.put("operatorId", operatorId);
        params.put("operatorName", operatorName);
        params.put("operatorTenantOwner", operatorTenantOwner);
        if (operatorMainDeptId != null) {
            params.put("operatorMainDeptId", operatorMainDeptId);
        }
        return params;
    }

    // ===================== 内部 =====================

    /** 包装 writer：每 500 行上报一次进度（按预估条数折算，封顶 95%）。 */
    private record ProgressReportingSink(ExportXlsxWriter writer, TaskContext ctx,
                                         long estimated) implements com.wensaas.core.api.exports.spi.ExportSink {

        @Override
        public void row(List<String> cells) {
            writer.row(cells);
            long rows = writer.rowsWritten();
            if (rows % 500 == 0) {
                int percent = estimated > 0
                        ? (int) Math.min(95, rows * 90 / Math.max(estimated, 1) + 5) : 50;
                ctx.updateProgress(percent, (int) Math.min(rows, Integer.MAX_VALUE));
            }
        }

        @Override
        public boolean stopped() {
            return writer.stopped();
        }
    }

    private void notifyCompleted(TaskContext ctx, long operatorId, String objectName,
                                 long rows, boolean truncated) {
        MessageService ms = messageServiceProvider.getIfAvailable();
        if (ms == null) {
            return;
        }
        try {
            ms.send(MessageSendRequest.builder("sys.task.export.completed")
                    .variables(Map.of(
                            "object_name", objectName == null ? "" : objectName,
                            "rows", String.valueOf(rows) + (truncated ? "（已达上限截断）" : ""),
                            "finish_time", LocalDateTime.now().format(TIME_FMT)))
                    .recipients(List.of(RecipientSpec.member(operatorId)))
                    .channels(List.of(Channel.STATION))
                    .category(MessageCategory.TODO)
                    .related("SysTask", String.valueOf(ctx.taskId()))
                    .link("/tasks")
                    .build());
        } catch (RuntimeException e) {
            log.warn("[export] 完成通知发送失败 taskId={}：{}", ctx.taskId(), e.getMessage());
        }
    }

    private void notifyFailed(TaskContext ctx, long operatorId, String objectName, String reason) {
        MessageService ms = messageServiceProvider.getIfAvailable();
        if (ms == null) {
            return;
        }
        try {
            ms.send(MessageSendRequest.builder("sys.task.failed")
                    .variables(Map.of(
                            "task_name", "导出" + (objectName == null ? "" : objectName),
                            "error_reason", reason == null || reason.isBlank() ? "未知原因" : reason))
                    .recipients(List.of(RecipientSpec.member(operatorId)))
                    .channels(List.of(Channel.STATION))
                    .category(MessageCategory.TODO)
                    .priority(Priority.IMPORTANT)
                    .link("/tasks")
                    .build());
        } catch (RuntimeException e) {
            log.warn("[export] 失败通知发送失败 taskId={}：{}", ctx.taskId(), e.getMessage());
        }
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> stringMap(Object o) {
        if (!(o instanceof Map<?, ?> m)) {
            return Map.of();
        }
        Map<String, String> out = new HashMap<>();
        ((Map<Object, Object>) m).forEach((k, v) -> {
            if (k != null && v != null) {
                out.put(String.valueOf(k), String.valueOf(v));
            }
        });
        return out;
    }
}
