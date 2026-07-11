package com.wensaas.core.web.compliance;

import com.wensaas.core.api.auth.permission.RequirePermission;
import com.wensaas.core.api.compliance.encryption.EncryptionOverview;
import com.wensaas.core.api.compliance.encryption.EncryptionService;
import com.wensaas.core.api.compliance.export.ExportRequestStatus;
import com.wensaas.core.api.compliance.OpsComplianceService;
import com.wensaas.core.web.common.PageQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 运营后台 · 数据隐私与合规审计。运营跨租户只读监控导出审批（按运营 dataScope
 * OPS_OWNED 仅看自己负责的租户收敛）+ 敏感字段加密底座自查。需 {@code ops.compliance.view}。
 */
@Tag(name = "ops-compliance", description = "运营后台 · 数据隐私与合规审计")
@SecurityRequirement(name = "bearer-jwt")
@RestController
@RequestMapping("/api/v1/ops/compliance")
public class OpsComplianceController {

    private final OpsComplianceService service;
    private final EncryptionService encryptionService;

    public OpsComplianceController(OpsComplianceService service, EncryptionService encryptionService) {
        this.service = service;
        this.encryptionService = encryptionService;
    }

    @Operation(summary = "跨租户超量导出审批审计列表",
            description = "按 tenantId / status / keyword 过滤；时间倒序；按运营 dataScope 收敛")
    @RequirePermission("ops.compliance.view")
    @GetMapping("/export-requests")
    public ExportRequestPageWebView list(@RequestParam(required = false) Long tenantId,
                                         @RequestParam(required = false) ExportRequestStatus status,
                                         @RequestParam(required = false) String keyword,
                                         @Valid @ParameterObject PageQuery pageQuery) {
        return ComplianceWebMappers.toWeb(service.list(
                new OpsComplianceService.OpsExportQuery(tenantId, status, keyword, pageQuery.page(), pageQuery.size())));
    }

    @Operation(summary = "跨租户导出申请详情")
    @RequirePermission("ops.compliance.view")
    @GetMapping("/export-requests/{id}")
    public ExportRequestWebView detail(@PathVariable long id) {
        return ComplianceWebMappers.toWeb(service.detail(id));
    }

    @Operation(summary = "敏感字段加密底座自查（算法 / 密钥版本 / 字段登记表 / 掩码规则）",
            description = "平台底座事实，全平台一致；属平台运维信息，不在租户端暴露")
    @RequirePermission("ops.compliance.view")
    @GetMapping("/encryption")
    public EncryptionOverview encryption() {
        return encryptionService.overview();
    }
}
