package com.wensaas.core.web.exports;

import com.wensaas.core.api.audit.Auditable;
import com.wensaas.core.api.exports.ExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

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
 * 业务数据导出框架 REST 入口。
 *
 * <p>发起后按合规策略分流：无需审批回 {@code SUBMITTED + taskId}（进度 / 下载走任务中心
 * {@code /api/v1/tasks/{id}}）；需审批回 {@code PENDING_APPROVAL + requestId}（审批通过后
 * 自动以申请人身份投递任务）。仅认证即可访问，按对象的发起权限在服务层校验（缺权限 403）。
 */
@Tag(name = "exports", description = "业务数据导出框架")
@SecurityRequirement(name = "bearer-jwt")
@RestController
@RequestMapping("/api/v1/exports")
public class ExportController {

    private final ExportService exportService;

    public ExportController(ExportService exportService) {
        this.exportService = exportService;
    }

    @Operation(summary = "可导出对象列表",
            description = "当前用户持发起权限的导出对象，含生效审批开关（requiresApproval=true 时发起必填 reason）")
    @GetMapping("/objects")
    public List<ExportObjectView> objects() {
        return exportService.listObjects().stream().map(ExportObjectView::from).toList();
    }

    @Operation(summary = "发起导出",
            description = "无需审批 → SUBMITTED + taskId；需审批 → PENDING_APPROVAL + requestId。"
                    + "对象未注册 422 / 无权限 403 / 需审批未填理由 422")
    @Auditable(module = "sys-export", action = "发起导出", objectType = "",
            objectIdExpr = "#req.objectCode()", objectDescExpr = "#req.objectCode()")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SubmitView submit(@Valid @RequestBody SubmitExportBody req) {
        ExportService.SubmitExportResult r = exportService.submit(new ExportService.SubmitExportCommand(
                req.objectCode(), req.filters(), req.filterSummary(), req.reason()));
        return new SubmitView(r.status().name().toLowerCase(Locale.ROOT), r.taskId(), r.requestId());
    }

    // ===================== 视图 / 请求 DTO =====================

    public record SubmitExportBody(
            @NotBlank String objectCode,
            Map<String, String> filters,
            @Size(max = 500) String filterSummary,
            @Size(max = 500) String reason) {
    }

    /** {@code status}: submitted / pending_approval；雪花 id 由 JSON 层序列化为字符串。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    public static class SubmitView {
        private String status;
        private Long taskId;
        private Long requestId;

    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    public static class ExportObjectView {
        private String code;
        private String name;
        private String module;
        private boolean requiresApproval;

        static ExportObjectView from(ExportService.ExportObjectOption o) {
            return new ExportObjectView(o.code(), o.name(), o.module(), o.requiresApproval());
        }
    }
}
