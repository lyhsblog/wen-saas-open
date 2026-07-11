package com.wensaas.core.web.ops;

import com.wensaas.core.api.auth.permission.RequirePermission;
import com.wensaas.core.api.ops.TenantOpsService;
import com.wensaas.core.web.ops.dto.OpsUserView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 运营后台 · 运营账号候选人查询。供开通向导/详情选「客户经理」用。
 */
@Tag(name = "ops-user", description = "运营后台 · 运营账号候选人")
@SecurityRequirement(name = "bearer-jwt")
@RestController
@RequestMapping("/api/v1/ops/users")
public class UserOpsController {

    private final TenantOpsService tenantOpsService;

    public UserOpsController(TenantOpsService tenantOpsService) {
        this.tenantOpsService = tenantOpsService;
    }

    @Operation(summary = "运营账号候选人", description = "tenant_id=0 且 status=1 的运营账号，按 username/realName 关键字过滤，最多 50 条")
    @RequirePermission("ops.tenant.create")
    @GetMapping
    public List<OpsUserView> listOpsUsers(@RequestParam(required = false) String keyword) {
        return tenantOpsService.listOpsUsers(keyword).stream()
                .map(u -> new OpsUserView(u.userId(), u.username(), u.realName()))
                .toList();
    }
}
