package com.wensaas.core.web.ops;

import com.wensaas.core.api.auth.permission.RequirePermission;
import com.wensaas.core.api.menu.PermissionCodeCatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 运营后台 · 权限码目录。菜单管理绑码时按 scope 选码用。
 */
@Tag(name = "ops-permission-code", description = "运营后台 · 权限码目录（菜单绑码选码）")
@SecurityRequirement(name = "bearer-jwt")
@RestController
@RequestMapping("/api/v1/ops/permission-codes")
public class OpsPermissionCodeController {

    private final PermissionCodeCatalogService service;

    public OpsPermissionCodeController(PermissionCodeCatalogService service) {
        this.service = service;
    }

    @Operation(summary = "按端列出可授权限码",
            description = "scope=1 OPS / 2 TENANT；菜单管理给 PAGE/ACTION 绑码时从此选")
    @RequirePermission("ops.menu.manage")
    @GetMapping
    public List<PermissionCodeCatalogService.PermissionCodeView> codes(@RequestParam int scope) {
        return service.codes(scope);
    }
}
