package com.wensaas.core.web.ops;

import com.wensaas.core.api.auth.permission.RequirePermission;
import com.wensaas.core.api.menu.MenuQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 运营后台 · 角色权限目录树（从 sys_menu 派生）。
 *
 * <p>供运营「角色与权限」页勾选：容器做聚合三态、可授 ops.* 码为叶子；附 {@code unbound}（待绑定孤儿码）。
 * 取代旧的 code 反推 {@code /api/v1/ops/team/roles/permission-tree}（前端 S6 切换后下线）。
 */
@Tag(name = "ops-permission", description = "运营后台 · 角色权限目录树")
@SecurityRequirement(name = "bearer-jwt")
@RestController
@RequestMapping("/api/v1/ops/permission-catalog")
public class OpsPermissionCatalogController {

    private final MenuQueryService menuQueryService;

    public OpsPermissionCatalogController(MenuQueryService menuQueryService) {
        this.menuQueryService = menuQueryService;
    }

    @Operation(summary = "运营角色权限目录树",
            description = "从 sys_menu（scope=OPS）派生的可勾选树：容器聚合 + 可授码叶子 + unbound 孤儿码")
    @RequirePermission("ops.team.role.list")
    @GetMapping
    public MenuQueryService.CatalogTree catalog() {
        // 运营端无套餐概念，ops 码非受控模块，门控集传空
        return menuQueryService.catalogTree(MenuQueryService.SCOPE_OPS, java.util.Set.of());
    }
}
