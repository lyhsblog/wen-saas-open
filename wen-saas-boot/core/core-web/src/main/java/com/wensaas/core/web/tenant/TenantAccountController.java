package com.wensaas.core.web.tenant;

import com.wensaas.core.api.auth.permission.RequirePermission;
import com.wensaas.core.api.tenant.selfservice.TenantAccountService;
import com.wensaas.core.web.common.PageQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 租户后台 · 系统管理 · 套餐与账号。
 *
 * <p>纯只读账户面板：套餐 / 席位 / 到期 / 变更历史。无任何写操作；续费 / 加购走运营线下。
 * 权限 {@code account.view}（主管理员恒可）。
 */
@Tag(name = "tenant-account", description = "租户后台 · 套餐与账号")
@SecurityRequirement(name = "bearer-jwt")
@RestController
@RequestMapping("/api/v1/base/account")
public class TenantAccountController {

    private final TenantAccountService service;

    public TenantAccountController(TenantAccountService service) {
        this.service = service;
    }

    @Operation(summary = "账户概况 + 套餐列表 + 加购汇总 + 客服联系信息")
    @RequirePermission("account.view")
    @GetMapping("/overview")
    public TenantAccountService.AccountOverview overview() {
        return service.getOverview();
    }

    @Operation(summary = "变更历史（运营操作快照，时间倒序，分页）")
    @RequirePermission("account.view")
    @GetMapping("/change-history")
    public TenantAccountService.ChangeHistoryPage changeHistory(@Valid @ParameterObject PageQuery pageQuery) {
        return service.listChangeHistory(pageQuery.page(), pageQuery.size());
    }
}
