package com.wensaas.core.impl.imports;

import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.file.FileService;
import com.wensaas.core.api.file.FileUploadRequest;
import com.wensaas.core.api.file.FileVisibility;
import com.wensaas.core.api.imports.ImportException;
import com.wensaas.core.api.imports.ImportService;
import com.wensaas.core.api.imports.ImportValidationResolver;
import com.wensaas.core.api.imports.ParseResult;
import com.wensaas.core.api.imports.PreviewFilter;
import com.wensaas.core.api.imports.PreviewRow;
import com.wensaas.core.api.spi.def.ImportEntityDef;
import com.wensaas.core.api.task.SubmitRequest;
import com.wensaas.core.api.task.TaskService;
import com.wensaas.core.api.task.TaskType;
import com.wensaas.core.api.tenant.TenantContext;
import com.wensaas.core.impl.spi.registry.ImportEntityRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 数据导入框架门面实现。
 *
 * <p>解析 / 校验在请求线程内同步完成（≤5000 行秒级）；执行走异步任务中心。
 *
 * <p>暂存策略：原始文件落文件中心，Redis 仅存元数据 + 轻量预览（错误/通过各 ≤200）。错误明细下载
 * 与执行 handler 按 fileId 重读 + 重校验，故不在 Redis 堆放全量行（避免多 MB 大 value）。
 *
 * <p>不走组件扫描，由 {@link ImportConfiguration} 与任务中心同条件装配（导入执行依赖 TaskService）。
 */
public class ImportServiceImpl implements ImportService {

    private static final Logger log = LoggerFactory.getLogger(ImportServiceImpl.class);
    private static final int PREVIEW_LIMIT = 200;
    private static final String XLSX_MIME =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String OWNER_MODULE = "import";

    private final ImportEntityRegistry registry;
    private final ImportValidationEngine engine;
    private final ImportSessionStore store;
    private final TaskService taskService;
    private final FileService fileService;
    private final Map<String, ImportValidationResolver> resolvers;

    public ImportServiceImpl(ImportEntityRegistry registry,
                             ImportValidationEngine engine,
                             ImportSessionStore store,
                             TaskService taskService,
                             FileService fileService,
                             List<ImportValidationResolver> resolverList) {
        this.registry = registry;
        this.engine = engine;
        this.store = store;
        this.taskService = taskService;
        this.fileService = fileService;
        this.resolvers = resolverList.stream()
                .collect(Collectors.toMap(ImportValidationResolver::entityCode, Function.identity()));
    }

    @Override
    public List<ImportEntityDef> listEntities() {
        return registry.getAll();
    }

    @Override
    public byte[] generateTemplate(String entityCode) {
        return ImportExcelSupport.writeTemplate(requireEntity(entityCode));
    }

    @Override
    public ParseResult parse(String entityCode, String fileName, InputStream content) {
        long tenantId = TenantContext.require();
        long creatorId = UserContext.require().userId();
        ImportEntityDef def = requireEntity(entityCode);

        byte[] bytes = readAll(content);
        // 先解析 + 校验（结构性错误在此抛出，未上传文件，不留孤儿）
        List<PreviewRow> all = validateAll(def, bytes);
        int total = all.size();
        int failed = (int) all.stream().filter(r -> !r.ok()).count();
        int passed = total - failed;

        // 结构 OK 才落文件中心
        String fileId = uploadSource(fileName, bytes);
        List<PreviewRow> errorPreview = capped(all.stream().filter(r -> !r.ok()));
        List<PreviewRow> passedPreview = capped(all.stream().filter(PreviewRow::ok));

        String sessionId = UUID.randomUUID().toString();
        store.save(new ImportSession(sessionId, tenantId, creatorId, entityCode, def.name(),
                def.taskHandlerCode(), fileName, fileId, total, passed, failed, errorPreview, passedPreview));
        log.info("import.parse ok entity={} file={} fileId={} total={} failed={} session={}",
                entityCode, fileName, fileId, total, failed, sessionId);
        return new ParseResult(sessionId, entityCode, fileName, total, passed, failed,
                merged(errorPreview, passedPreview));
    }

    @Override
    public ParseResult getSession(String sessionId, PreviewFilter filter) {
        ImportSession s = requireSession(sessionId);
        List<PreviewRow> preview = switch (filter == null ? PreviewFilter.ALL : filter) {
            case ALL -> merged(s.errorPreview(), s.passedPreview());
            case ERROR -> s.errorPreview();
            case PASSED -> s.passedPreview();
        };
        return new ParseResult(s.sessionId(), s.entityCode(), s.fileName(), s.total(), s.passed(),
                s.failed(), preview);
    }

    @Override
    public byte[] generateErrorReport(String sessionId) {
        ImportSession s = requireSession(sessionId);
        ImportEntityDef def = requireEntity(s.entityCode());
        // 重读原文件 + 重校验，拿到全量错误行（非仅预览 200）
        List<PreviewRow> errs = validateAll(def, fileService.readContent(s.fileId())).stream()
                .filter(r -> !r.ok()).toList();
        return ImportExcelSupport.writeErrorReport(def, errs);
    }

    @Override
    public Long confirm(String sessionId) {
        ImportSession s = requireSession(sessionId);
        if (s.failed() > 0) {
            throw new ImportException(ImportException.Code.HAS_ERRORS,
                    "存在 " + s.failed() + " 条错误行，请修正后重新上传");
        }
        String name = "导入" + s.entityName() + "-" + s.fileName();
        // 执行参数带 fileId：handler 按文件重读重校验，不依赖会话存活
        // 透传操作人，供 handler 在异步执行线程构造导入审计（TaskContext 不含 operator）
        var principal = UserContext.require();
        Long taskId = taskService.submit(new SubmitRequest(TaskType.IMPORT, name, s.taskHandlerCode(),
                Map.of("sessionId", s.sessionId(), "fileId", s.fileId(),
                        "entityCode", s.entityCode(), "fileName", s.fileName(),
                        "operatorId", principal.userId(), "operatorName", principal.username())));
        log.info("import.confirm ok session={} entity={} fileId={} taskId={}",
                sessionId, s.entityCode(), s.fileId(), taskId);
        return taskId;
    }

    /** 读取 + 全量校验（解析结构性错误在 readRows 抛出）；预览路径只消费行，不消费关联 id 映射。 */
    private List<PreviewRow> validateAll(ImportEntityDef def, byte[] bytes) {
        return engine.validate(def, ImportExcelSupport.readRows(def, new ByteArrayInputStream(bytes)),
                resolvers.get(def.code())).rows();
    }

    private String uploadSource(String fileName, byte[] bytes) {
        FileUploadRequest req = new FileUploadRequest(
                fileName == null || fileName.isBlank() ? "import.xlsx" : fileName,
                XLSX_MIME, bytes.length, new ByteArrayInputStream(bytes),
                FileVisibility.BUSINESS_ONLY, OWNER_MODULE, "导入源文件", null, null);
        return fileService.uploadDirect(req).fileId();
    }

    private ImportEntityDef requireEntity(String entityCode) {
        return registry.findByKey(entityCode).orElseThrow(() ->
                new ImportException(ImportException.Code.ENTITY_UNKNOWN, "导入实体未注册: " + entityCode));
    }

    /** 加载会话并校验租户归属；跨租户视作不存在。 */
    private ImportSession requireSession(String sessionId) {
        ImportSession s = store.load(sessionId).orElseThrow(() ->
                new ImportException(ImportException.Code.SESSION_NOT_FOUND, "预览会话不存在或已过期"));
        if (s.tenantId() != TenantContext.require()) {
            throw new ImportException(ImportException.Code.SESSION_NOT_FOUND, "预览会话不存在或已过期");
        }
        return s;
    }

    private static byte[] readAll(InputStream in) {
        try {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("读取上传内容失败", e);
        }
    }

    /** 按行号排序取前 200。 */
    private static List<PreviewRow> capped(Stream<PreviewRow> rows) {
        return rows.sorted(Comparator.comparingInt(PreviewRow::rowNo)).limit(PREVIEW_LIMIT).toList();
    }

    /** 错误优先合并，取前 200（[全部] 视图）。 */
    private static List<PreviewRow> merged(List<PreviewRow> errorPreview, List<PreviewRow> passedPreview) {
        List<PreviewRow> out = new ArrayList<>(errorPreview);
        for (PreviewRow p : passedPreview) {
            if (out.size() >= PREVIEW_LIMIT) {
                break;
            }
            out.add(p);
        }
        return out;
    }
}
