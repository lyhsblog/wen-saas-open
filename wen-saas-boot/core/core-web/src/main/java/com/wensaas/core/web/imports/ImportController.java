package com.wensaas.core.web.imports;

import com.wensaas.core.api.imports.ImportService;
import com.wensaas.core.api.imports.ParseResult;
import com.wensaas.core.api.imports.PreviewFilter;
import com.wensaas.core.api.imports.PreviewRow;
import com.wensaas.core.api.imports.RowError;
import com.wensaas.core.api.spi.def.ImportEntityDef;
import com.wensaas.core.api.spi.def.ImportFieldDef;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * 数据导入框架 REST 入口。
 *
 * <p>解析 / 预览同步完成；执行进度与结果下载复用任务中心 {@code /api/v1/tasks/{id}/stream|download}。
 * 仅认证即可访问，数据范围由各导入实体的 resolver / handler 按租户隔离。
 */
@Tag(name = "imports", description = "数据导入框架")
@SecurityRequirement(name = "bearer-jwt")
@RestController
@RequestMapping("/api/v1/imports")
public class ImportController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ImportService importService;

    public ImportController(ImportService importService) {
        this.importService = importService;
    }

    @Operation(summary = "可导入实体列表", description = "前端「导入」入口渲染用，含字段定义")
    @GetMapping("/entities")
    public List<ImportEntityView> entities() {
        return importService.listEntities().stream().map(ImportEntityView::from).toList();
    }

    @Operation(summary = "下载导入模板", description = "按字段定义生成 xlsx（表头 + 示例行）")
    @GetMapping("/{entityCode}/template")
    public ResponseEntity<byte[]> template(@PathVariable String entityCode) {
        byte[] body = importService.generateTemplate(entityCode);
        return xlsxResponse(body, entityCode + "-导入模板.xlsx");
    }

    @Operation(summary = "上传解析 + 预校验",
            description = "同步解析 + 全量校验，落预览暂存；返回 sessionId + 统计 + 前 200 行预览。文件损坏 / 表头不符 → 422")
    @PostMapping("/{entityCode}/parse")
    public ParseResultView parse(@PathVariable String entityCode,
                                 @RequestParam("file") MultipartFile file) {
        try {
            ParseResult r = importService.parse(entityCode, file.getOriginalFilename(), file.getInputStream());
            return ParseResultView.from(r);
        } catch (IOException e) {
            throw new UncheckedIOException("读取上传文件失败", e);
        }
    }

    @Operation(summary = "重取预览", description = "按 filter（all/passed/error）分页取暂存预览")
    @GetMapping("/sessions/{sessionId}")
    public ParseResultView session(@PathVariable String sessionId,
                                   @Parameter(description = "all / passed / error")
                                   @RequestParam(value = "filter", required = false) String filter) {
        PreviewFilter pf = parseFilter(filter);
        return ParseResultView.from(importService.getSession(sessionId, pf));
    }

    @Operation(summary = "下载错误明细", description = "仅错误行：原数据列 + 错误说明列")
    @GetMapping("/sessions/{sessionId}/errors")
    public ResponseEntity<byte[]> errors(@PathVariable String sessionId) {
        byte[] body = importService.generateErrorReport(sessionId);
        return xlsxResponse(body, "错误明细.xlsx");
    }

    @Operation(summary = "确认导入",
            description = "全行通过才提交任务中心，返回 taskId；有错行 → 409。进度 / 结果走 /api/v1/tasks/{taskId}")
    @PostMapping("/sessions/{sessionId}/confirm")
    public ConfirmView confirm(@PathVariable String sessionId) {
        return new ConfirmView(importService.confirm(sessionId));
    }

    private static PreviewFilter parseFilter(String raw) {
        if (raw == null || raw.isBlank()) {
            return PreviewFilter.ALL;
        }
        try {
            return PreviewFilter.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "未知的 filter: " + raw);
        }
    }

    private static ResponseEntity<byte[]> xlsxResponse(byte[] body, String filename) {
        ContentDisposition cd = ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION, cd.toString())
                .body(body);
    }

    // ===================== 视图 DTO =====================

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    public static class ImportEntityView {
        private String code;
        private String name;
        private boolean needPreview;
        private int rowLimit;
        private List<ImportFieldView> fields;

        static ImportEntityView from(ImportEntityDef d) {
            return new ImportEntityView(d.code(), d.name(), d.needPreview(), d.rowLimit(),
                    d.fields().stream().map(ImportFieldView::from).toList());
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    public static class ImportFieldView {
        private String header;
        private String key;
        private String type;
        private boolean required;
        private String example;

        static ImportFieldView from(ImportFieldDef f) {
            return new ImportFieldView(f.header(), f.key(), f.type().name().toLowerCase(Locale.ROOT),
                    f.required(), f.example());
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    public static class ParseResultView {
        private String sessionId;
        private String entityCode;
        private String fileName;
        private int total;
        private int passed;
        private int failed;
        private List<PreviewRowView> preview;

        static ParseResultView from(ParseResult r) {
            return new ParseResultView(r.sessionId(), r.entityCode(), r.fileName(), r.total(),
                    r.passed(), r.failed(), r.preview().stream().map(PreviewRowView::from).toList());
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    public static class PreviewRowView {
        private int rowNo;
        private int excelRow;
        private Map<String, String> cells;
        private String status;
        private List<RowErrorView> errors;

        static PreviewRowView from(PreviewRow p) {
            return new PreviewRowView(p.rowNo(), p.excelRow(), p.cells(), p.ok() ? "ok" : "error",
                    p.errors().stream().map(RowErrorView::from).toList());
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    public static class RowErrorView {
        private String field;
        private String code;
        private String message;

        static RowErrorView from(RowError e) {
            return new RowErrorView(e.field(), e.code().name().toLowerCase(Locale.ROOT), e.message());
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    public static class ConfirmView {
        private Long taskId;

    }
}
