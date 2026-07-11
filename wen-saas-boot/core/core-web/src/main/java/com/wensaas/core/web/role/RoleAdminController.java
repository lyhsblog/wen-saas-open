package com.wensaas.core.web.role;

import com.wensaas.core.api.namesource.EntityTypes;
import com.wensaas.core.api.audit.Auditable;
import com.wensaas.core.api.auth.permission.PermissionCatalogService;
import com.wensaas.core.api.auth.permission.RequirePermission;
import com.wensaas.core.api.auth.permission.RoleAdminService;
import com.wensaas.core.web.role.dto.MemberIdsRequest;
import com.wensaas.core.web.role.dto.RoleCreateRequest;
import com.wensaas.core.web.role.dto.RolePermissionsUpdateRequest;
import com.wensaas.core.web.role.dto.RoleUpdateRequest;
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
 * 角色管理（租户后台）。
 */
@Tag(name = "tenant-role", description = "租户后台 · 角色与权限")
@SecurityRequirement(name = "bearer-jwt")
@RestController
@RequestMapping("/api/v1/base/roles")
public class RoleAdminController {

    private final RoleAdminService service;
    private final PermissionCatalogService permissionCatalogService;

    public RoleAdminController(RoleAdminService service, PermissionCatalogService permissionCatalogService) {
        this.service = service;
        this.permissionCatalogService = permissionCatalogService;
    }

    @Operation(summary = "列出当前租户角色")
    @RequirePermission("role.list")
    @GetMapping
    public List<RoleAdminService.RoleView> listRoles() {
        return service.listRoles();
    }

    @Operation(summary = "取角色详情",
            description = "含 memberCount + permissions 列表")
    @RequirePermission("role.list")
    @GetMapping("/{roleId}")
    public RoleAdminService.RoleDetail getRole(@PathVariable long roleId) {
        return service.getRole(roleId);
    }

    @Operation(summary = "新建角色")
    @RequirePermission("role.create")
    @Auditable(module = "__core__", action = "新建角色", objectType = EntityTypes.SYS_ROLE,
            objectIdExpr = "#result.id()", objectDescExpr = "#result.name()")
    @PostMapping
    public RoleAdminService.RoleDetail create(@Valid @RequestBody RoleCreateRequest req) {
        return service.create(new RoleAdminService.CreateRoleRequest(
                req.code(), req.name(), req.description(), req.dataScope()));
    }

    @Operation(summary = "编辑角色基本信息（不含权限点 / 成员）")
    @RequirePermission("role.update")
    @Auditable(module = "__core__", action = "编辑角色", objectType = EntityTypes.SYS_ROLE,
            objectIdExpr = "#roleId")
    @PutMapping("/{roleId}")
    public RoleAdminService.RoleDetail update(@PathVariable long roleId,
                                                @Valid @RequestBody RoleUpdateRequest req) {
        return service.update(roleId, new RoleAdminService.UpdateRoleRequest(
                req.name(), req.description(), req.dataScope()));
    }

    @Operation(summary = "删除角色（仅自定义；要求无成员）")
    @RequirePermission("role.delete")
    @Auditable(module = "__core__", action = "删除角色", objectType = EntityTypes.SYS_ROLE,
            objectIdExpr = "#roleId")
    @DeleteMapping("/{roleId}")
    public ResponseEntity<Void> delete(@PathVariable long roleId) {
        service.delete(roleId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "启用 / 停用角色")
    @RequirePermission("role.update")
    @Auditable(module = "__core__", action = "切换角色状态", objectType = EntityTypes.SYS_ROLE,
            objectIdExpr = "#roleId", objectDescExpr = "#result.name()")
    @PutMapping("/{roleId}/status")
    public RoleAdminService.RoleDetail setStatus(@PathVariable long roleId,
                                                   @RequestParam boolean enabled) {
        return service.setStatus(roleId, enabled);
    }

    @Operation(summary = "全量替换角色权限点",
            description = "传空数组 = 清空所有权限；写入后所有租户用户权限缓存全清")
    @RequirePermission("role.update")
    @Auditable(module = "__core__", action = "更新权限", objectType = EntityTypes.SYS_ROLE,
            objectIdExpr = "#roleId", objectDescExpr = "#result.name()")
    @PutMapping("/{roleId}/permissions")
    public RoleAdminService.RoleDetail updatePermissions(@PathVariable long roleId,
                                                           @Valid @RequestBody RolePermissionsUpdateRequest req) {
        return service.updatePermissions(roleId, req.codes());
    }

    @Operation(summary = "列出角色成员")
    @RequirePermission("role.list")
    @GetMapping("/{roleId}/members")
    public List<RoleAdminService.MemberRef> listMembers(@PathVariable long roleId) {
        return service.listMembers(roleId);
    }

    @Operation(summary = "批量分配成员到角色",
            description = "已存在的关联自动跳过；新增成员触发该用户权限缓存 evict")
    @RequirePermission("role.update")
    @Auditable(module = "__core__", action = "分配成员", objectType = EntityTypes.SYS_ROLE,
            objectIdExpr = "#roleId", objectDescExpr = "#result.name()")
    @PostMapping("/{roleId}/members")
    public RoleAdminService.RoleDetail assignMembers(@PathVariable long roleId,
                                                       @Valid @RequestBody MemberIdsRequest req) {
        return service.assignMembers(roleId, req.userIds());
    }

    @Operation(summary = "批量移除角色成员")
    @RequirePermission("role.update")
    @Auditable(module = "__core__", action = "移除成员", objectType = EntityTypes.SYS_ROLE,
            objectIdExpr = "#roleId", objectDescExpr = "#result.name()")
    @DeleteMapping("/{roleId}/members")
    public RoleAdminService.RoleDetail removeMembers(@PathVariable long roleId,
                                                       @Valid @RequestBody MemberIdsRequest req) {
        return service.removeMembers(roleId, req.userIds());
    }

    @Operation(summary = "取角色数据范围配置（角色默认 + action 覆盖）",
            description = "数据来源 sys_role_data_scope；供数据范围配置页加载")
    @RequirePermission("role.list")
    @GetMapping("/{roleId}/data-scope")
    public RoleAdminService.RoleDataScopeView getRoleDataScope(@PathVariable long roleId) {
        return service.getRoleDataScope(roleId);
    }

    @Operation(summary = "保存角色数据范围配置（全量替换）",
            description = "同时更新 sys_role.data_scope + sys_role_data_scope；写后 evictAll")
    @RequirePermission("role.update")
    @Auditable(module = "__core__", action = "更新数据范围", objectType = EntityTypes.SYS_ROLE,
            objectIdExpr = "#roleId")
    @PutMapping("/{roleId}/data-scope")
    public ResponseEntity<Void> saveRoleDataScope(@PathVariable long roleId,
                                                    @RequestBody RoleAdminService.SaveDataScopeCmd cmd) {
        service.saveRoleDataScope(roleId, cmd);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "列出所有 @DataPermission 数据权限 action",
            description = "前端数据范围配置页「按功能点单独配置」选择列表")
    @RequirePermission("role.list")
    @GetMapping("/data-permission-actions")
    public List<PermissionCatalogService.DataPermissionActionDef> listDataPermissionActions() {
        return permissionCatalogService.listDataPermissionActions();
    }
}
