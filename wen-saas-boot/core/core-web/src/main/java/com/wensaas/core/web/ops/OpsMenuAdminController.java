package com.wensaas.core.web.ops;

import com.wensaas.core.api.audit.Auditable;
import com.wensaas.core.api.auth.permission.RequirePermission;
import com.wensaas.core.api.menu.MenuAdminService;
import com.wensaas.core.api.menu.MenuAdminService.CreateMenuCommand;
import com.wensaas.core.api.menu.MenuAdminService.UpdateMenuCommand;
import com.wensaas.core.web.ops.dto.MenuMoveRequest;
import com.wensaas.core.web.ops.dto.MenuSaveRequest;
import com.wensaas.core.web.ops.dto.MenuStatusRequest;
import com.wensaas.core.web.ops.dto.MenuUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 运营后台 · 菜单管理（写 {@code sys_menu}）。
 *
 * <p>运营自助维护菜单树，兑现「加菜单不必后端发版」。SEED 节点仅可改 / 启停，ADMIN 节点可删。
 * 保存绑码必须 ∈ 后端权限码目录（{@code /api/v1/ops/permission-codes}）。
 */
@Tag(name = "ops-menu", description = "运营后台 · 菜单管理")
@SecurityRequirement(name = "bearer-jwt")
@RestController
@RequestMapping("/api/v1/ops/menus")
public class OpsMenuAdminController {

    private final MenuAdminService service;

    public OpsMenuAdminController(MenuAdminService service) {
        this.service = service;
    }

    @Operation(summary = "菜单管理全量树（含停用节点）")
    @RequirePermission("ops.menu.manage")
    @GetMapping
    public List<MenuAdminService.MenuAdminNode> tree(@RequestParam int scope) {
        return service.tree(scope);
    }

    @Operation(summary = "新建菜单节点")
    @RequirePermission("ops.menu.manage")
    @Auditable(module = "ops", action = "新建菜单", objectIdExpr = "#result.id()", objectDescExpr = "#result.label()")
    @PostMapping
    public MenuAdminService.MenuAdminNode create(@Valid @RequestBody MenuSaveRequest req) {
        return service.create(new CreateMenuCommand(req.scope(), req.parentKey(), req.nodeType(),
                req.label(), req.icon(), req.path(), req.permissionCode(), req.description(), req.sort()));
    }

    @Operation(summary = "编辑菜单节点属性")
    @RequirePermission("ops.menu.manage")
    @Auditable(module = "ops", action = "编辑菜单", objectIdExpr = "#id", objectDescExpr = "#result.label()")
    @PutMapping("/{id}")
    public MenuAdminService.MenuAdminNode update(@PathVariable long id,
                                                 @Valid @RequestBody MenuUpdateRequest req) {
        return service.update(id, new UpdateMenuCommand(req.label(), req.icon(), req.path(),
                req.permissionCode(), req.description(), req.sort()));
    }

    @Operation(summary = "移动 / 排序菜单节点")
    @RequirePermission("ops.menu.manage")
    @Auditable(module = "ops", action = "移动菜单", objectIdExpr = "#id")
    @PutMapping("/{id}/move")
    public ResponseEntity<Void> move(@PathVariable long id, @Valid @RequestBody MenuMoveRequest req) {
        service.move(id, req.parentKey(), req.sort());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "启用 / 停用菜单节点")
    @RequirePermission("ops.menu.manage")
    @Auditable(module = "ops", action = "更新菜单状态", objectIdExpr = "#id", objectDescExpr = "#result.label()")
    @PutMapping("/{id}/status")
    public MenuAdminService.MenuAdminNode setStatus(@PathVariable long id,
                                                    @Valid @RequestBody MenuStatusRequest req) {
        return service.setStatus(id, req.enabled());
    }

    @Operation(summary = "删除菜单节点（仅 ADMIN 节点、且无子节点）")
    @RequirePermission("ops.menu.manage")
    @Auditable(module = "ops", action = "删除菜单", objectIdExpr = "#id")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
