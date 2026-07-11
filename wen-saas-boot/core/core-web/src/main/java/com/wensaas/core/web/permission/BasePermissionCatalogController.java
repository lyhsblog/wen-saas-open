package com.wensaas.core.web.permission;

import com.wensaas.core.api.auth.permission.RequirePermission;
import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.menu.MenuQueryService;
import com.wensaas.core.impl.pkg.PackageModuleGate;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 租户后台 · 角色权限目录树（从 sys_menu 派生）。
 *
 * <p>供租户「角色 → 功能权限 Tab」勾选：容器做聚合三态、可授码为叶子；附 {@code unbound}（待绑定孤儿码）。
 * 取代旧的 code 反推 {@code /api/v1/tenant/permissions/catalog}（前端 S6 切换后下线）。
 *
 * <p>路径 {@code /api/v1/base}：命名空间分层（S2）落地后底座统一前缀，本端点先就位。
 */
@Tag(name = "base-permission", description = "租户后台 · 角色权限目录树")
@SecurityRequirement(name = "bearer-jwt")
@RestController
@RequestMapping("/api/v1/base/permission-catalog")
public class BasePermissionCatalogController {

    private final MenuQueryService menuQueryService;
    private final PackageModuleGate packageGate;

    public BasePermissionCatalogController(MenuQueryService menuQueryService,
                                           PackageModuleGate packageGate) {
        this.menuQueryService = menuQueryService;
        this.packageGate = packageGate;
    }

    @Operation(summary = "租户角色权限目录树",
            description = "从 sys_menu（scope=TENANT）派生的可勾选树：容器聚合 + 可授码叶子 + unbound 孤儿码；"
                    + "未开通的受控业务模块（套餐）整段隐去，不可勾选")
    @RequirePermission("role.list")
    @GetMapping
    public MenuQueryService.CatalogTree catalog() {
        // 套餐门控：以当前租户已开通（active∪过期只读）模块集做过滤，未购模块码不进角色目录
        long tenantId = UserContext.require().tenantId();
        return menuQueryService.catalogTree(MenuQueryService.SCOPE_TENANT,
                packageGate.enabledModuleCodes(tenantId));
    }
}
