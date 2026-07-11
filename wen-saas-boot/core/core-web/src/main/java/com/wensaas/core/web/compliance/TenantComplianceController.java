package com.wensaas.core.web.compliance;

import com.wensaas.core.api.audit.Auditable;
import com.wensaas.core.api.auth.permission.RequirePermission;
import com.wensaas.core.api.compliance.ComplianceOverview;
import com.wensaas.core.api.compliance.ComplianceService;
import com.wensaas.core.api.compliance.export.DataExportMode;
import com.wensaas.core.api.compliance.export.DataExportService;
import com.wensaas.core.api.compliance.export.ExportPolicyView;
import com.wensaas.core.api.compliance.export.ExportRequestStatus;
import com.wensaas.core.api.compliance.retention.RetentionOverview;
import com.wensaas.core.api.compliance.retention.RetentionService;
import com.wensaas.core.web.common.PageQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 租户后台 · 数据隐私与合规。
 *
 * <p>发起导出申请 / 查看自己的申请：所有租户成员；查看本租户全部 / 审批：主管理员或持
 * {@code base.compliance.export.approve} 的角色；策略读写：{@code base.compliance.config}。主管理员在
 * {@code @RequirePermission} 中跳过所有规则。
 */
@Tag(name = "tenant-compliance", description = "租户后台 · 数据隐私与合规")
@SecurityRequirement(name = "bearer-jwt")
@RestController
@RequestMapping("/api/v1/base/compliance")
public class TenantComplianceController {

    private final ComplianceService service;
    private final DataExportService dataExportService;
    private final RetentionService retentionService;

    public TenantComplianceController(ComplianceService service, DataExportService dataExportService,
                                      RetentionService retentionService) {
        this.service = service;
        this.dataExportService = dataExportService;
        this.retentionService = retentionService;
    }

    @Operation(summary = "合规总览指标（超量导出审批维度）")
    @RequirePermission("base.compliance.config")
    @GetMapping("/overview")
    public ComplianceOverview overview() {
        return service.overview();
    }

    @Operation(summary = "发起导出申请（按对象审批开关：需审批转 PENDING 并通知审批人，无需审批回 409）")
    @Auditable(module = "compliance", action = "申请导出", objectType = "",
            objectIdExpr = "#result.id()", objectDescExpr = "#result.requestNo()")
    @PostMapping("/export-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public ExportRequestWebView submit(@Valid @RequestBody SubmitExportRequest req) {
        return ComplianceWebMappers.toWeb(service.submit(new ComplianceService.SubmitCommand(
                req.objectType(), req.objectCode(), req.filterSummary(),
                req.estimatedCount() == null ? 0 : req.estimatedCount(), req.reason(), null)));
    }

    @Operation(summary = "我发起的导出申请（可按状态过滤）")
    @GetMapping("/export-requests/mine")
    public ExportRequestPageWebView myRequests(@RequestParam(required = false) ExportRequestStatus status,
                                               @Valid @ParameterObject PageQuery pageQuery) {
        return ComplianceWebMappers.toWeb(service.listRequests(status, true, pageQuery.page(), pageQuery.size()));
    }

    @Operation(summary = "本租户全部导出申请（需审批权限）")
    @RequirePermission("base.compliance.export.approve")
    @GetMapping("/export-requests")
    public ExportRequestPageWebView allRequests(@RequestParam(required = false) ExportRequestStatus status,
                                                @Valid @ParameterObject PageQuery pageQuery) {
        return ComplianceWebMappers.toWeb(service.listRequests(status, false, pageQuery.page(), pageQuery.size()));
    }

    @Operation(summary = "导出申请详情（本人 / 审批人可见）")
    @GetMapping("/export-requests/{id}")
    public ExportRequestWebView detail(@PathVariable long id) {
        return ComplianceWebMappers.toWeb(service.detail(id));
    }

    @Operation(summary = "批准导出申请")
    @RequirePermission("base.compliance.export.approve")
    @Auditable(module = "compliance", action = "通过导出申请", objectType = "",
            objectIdExpr = "#id")
    @PostMapping("/export-requests/{id}/approve")
    public ResponseEntity<Void> approve(@PathVariable long id, @Valid @RequestBody DecisionRequest req) {
        service.approve(id, req.comment());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "拒绝导出申请")
    @RequirePermission("base.compliance.export.approve")
    @Auditable(module = "compliance", action = "驳回导出申请", objectType = "",
            objectIdExpr = "#id")
    @PostMapping("/export-requests/{id}/reject")
    public ResponseEntity<Void> reject(@PathVariable long id, @Valid @RequestBody DecisionRequest req) {
        service.reject(id, req.comment());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "查看超量导出策略")
    @RequirePermission("base.compliance.config")
    @GetMapping("/policy")
    public ExportPolicyView getPolicy() {
        return service.getPolicy();
    }

    @Operation(summary = "更新超量导出策略（各对象审批开关 / 审批权限 / 超时提醒）")
    @RequirePermission("base.compliance.config")
    @Auditable(module = "compliance", action = "更新策略", objectType = "")
    @PutMapping("/policy")
    public ResponseEntity<Void> updatePolicy(@Valid @RequestBody UpdatePolicyRequest req) {
        List<ComplianceService.ObjectToggle> toggles = req.objects() == null ? List.of()
                : req.objects().stream()
                        .map(o -> new ComplianceService.ObjectToggle(o.code(), o.requiresApproval()))
                        .toList();
        service.updatePolicy(new ComplianceService.UpdatePolicyCommand(
                toggles, req.approvalAuthority(), req.timeoutHours()));
        return ResponseEntity.noContent().build();
    }

    // ===================== 数据留存与软删除策略 =====================

    @Operation(summary = "数据留存与软删除策略总览（策略矩阵 + 文件回收站实时统计）")
    @RequirePermission("base.compliance.config")
    @GetMapping("/retention")
    public RetentionOverview retention() {
        return retentionService.overview();
    }

    // ===================== 客户全量数据导出（GDPR-style） =====================

    @Operation(summary = "发起全量数据导出（仅主管理员，必填理由，月限 1 次）")
    @Auditable(module = "compliance", action = "申请数据导出", objectType = "",
            objectIdExpr = "#result.id()", objectDescExpr = "#result.requestNo()")
    @PostMapping("/data-exports")
    @ResponseStatus(HttpStatus.CREATED)
    public DataExportWebView submitDataExport(@Valid @RequestBody SubmitDataExportRequest req) {
        DataExportMode mode = req.dataMode() == null ? DataExportMode.MASKED : req.dataMode();
        return ComplianceWebMappers.toWeb(dataExportService.submit(
                new DataExportService.SubmitCommand(req.reason(), mode)));
    }

    @Operation(summary = "本租户全量数据导出申请列表")
    @GetMapping("/data-exports")
    public DataExportPageWebView listDataExports(@Valid @ParameterObject PageQuery pageQuery) {
        return ComplianceWebMappers.toWeb(dataExportService.list(pageQuery.page(), pageQuery.size()));
    }

    @Operation(summary = "全量数据导出申请详情（主管理员 / 发起人可见）")
    @GetMapping("/data-exports/{id}")
    public DataExportWebView dataExportDetail(@PathVariable long id) {
        return ComplianceWebMappers.toWeb(dataExportService.detail(id));
    }

    @Operation(summary = "下载全量导出包（换文件中心签名 URL；校验完成 + 7 天有效期）")
    @Auditable(module = "compliance", action = "下载导出数据", objectType = "",
            objectIdExpr = "#id")
    @GetMapping("/data-exports/{id}/download")
    public java.util.Map<String, Object> downloadDataExport(@PathVariable long id) {
        return java.util.Map.of("url", dataExportService.downloadUrl(id));
    }

    // ===================== 请求 DTO =====================

    public record SubmitDataExportRequest(
            @NotBlank @Size(max = 512) String reason,
            DataExportMode dataMode) {
    }

    public record SubmitExportRequest(
            @Size(max = 64) String objectType,
            @NotBlank @Size(max = 64) String objectCode,
            @Size(max = 512) String filterSummary,
            @PositiveOrZero Integer estimatedCount,
            @NotBlank @Size(max = 512) String reason) {
    }

    public record DecisionRequest(@Size(max = 512) String comment) {}

    public record UpdatePolicyRequest(
            @Valid List<ObjectToggleRequest> objects,
            @NotBlank String approvalAuthority,
            Integer timeoutHours) {
    }

    public record ObjectToggleRequest(
            @NotBlank @Size(max = 64) String code,
            boolean requiresApproval) {
    }
}
