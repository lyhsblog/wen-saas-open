package com.wensaas.core.web.ops;

import com.wensaas.core.api.namesource.EntityTypes;
import com.wensaas.core.api.audit.Auditable;
import com.wensaas.core.api.auth.permission.RequirePermission;
import com.wensaas.core.api.ops.team.OpsTeamRoleAdminService;
import com.wensaas.core.web.ops.dto.OpsRoleCreateRequest;
import com.wensaas.core.web.ops.dto.OpsRoleMembersRequest;
import com.wensaas.core.web.ops.dto.OpsRolePermissionsUpdateRequest;
import com.wensaas.core.web.ops.dto.OpsRoleUpdateRequest;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 运营后台 · 角色与权限。
 *
 * <p>2 预置角色（OPS_SUPER_ADMIN / OPS_CSM）不可删除 / 改 code，可改 name / description / dataScope。
 * OPS_SUPER_ADMIN 的权限集只读（系统按已注册权限点维护）、成员变更仅限超管本人；
 * OPS_CSM 的权限 / 成员可编辑。
 */
@Tag(name = "ops-team-role", description = "运营角色与权限")
@SecurityRequirement(name = "bearer-jwt")
@RestController
@RequestMapping("/api/v1/ops/team/roles")
public class OpsTeamRoleController {

    private final OpsTeamRoleAdminService service;

    public OpsTeamRoleController(OpsTeamRoleAdminService service) {
        this.service = service;
    }

    @Operation(summary = "运营角色列表（预置在前 + 自定义在后）")
    @RequirePermission("ops.team.role.list")
    @GetMapping
    public List<OpsTeamRoleAdminService.OpsRoleListItem> list() {
        return service.listRoles();
    }

    @Operation(summary = "运营角色详情（含权限点 code 列表）")
    @RequirePermission("ops.team.role.list")
    @GetMapping("/{roleId}")
    public OpsTeamRoleAdminService.OpsRoleDetail get(@PathVariable long roleId) {
        return service.getRole(roleId);
    }

    @Operation(summary = "新建自定义角色")
    @RequirePermission("ops.team.role.create")
    @Auditable(module = "ops", action = "新建角色", objectType = EntityTypes.SYS_ROLE,
            objectIdExpr = "#result.id()", objectDescExpr = "#result.code()")
    @PostMapping
    public OpsTeamRoleAdminService.OpsRoleDetail create(@Valid @RequestBody OpsRoleCreateRequest req) {
        return service.create(new OpsTeamRoleAdminService.CreateRoleCommand(
                req.code(), req.name(), req.description(), req.dataScope()));
    }

    @Operation(summary = "编辑角色基本信息（不含权限 / 成员）")
    @RequirePermission("ops.team.role.update")
    @Auditable(module = "ops", action = "编辑角色", objectType = EntityTypes.SYS_ROLE, objectIdExpr = "#roleId")
    @PutMapping("/{roleId}")
    public OpsTeamRoleAdminService.OpsRoleDetail update(@PathVariable long roleId,
                                                        @Valid @RequestBody OpsRoleUpdateRequest req) {
        return service.update(roleId, new OpsTeamRoleAdminService.UpdateRoleCommand(
                req.name(), req.description(), req.dataScope()));
    }

    @Operation(summary = "删除（预置角色禁止；要求无成员）")
    @RequirePermission("ops.team.role.delete")
    @Auditable(module = "ops", action = "删除角色", objectType = EntityTypes.SYS_ROLE, objectIdExpr = "#roleId")
    @DeleteMapping("/{roleId}")
    public ResponseEntity<Void> delete(@PathVariable long roleId) {
        service.delete(roleId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "全量替换权限点（OPS_SUPER_ADMIN 只读）")
    @RequirePermission("ops.team.role.assign_permission")
    @Auditable(module = "ops", action = "分配权限", objectType = EntityTypes.SYS_ROLE, objectIdExpr = "#roleId")
    @PutMapping("/{roleId}/permissions")
    public OpsTeamRoleAdminService.OpsRoleDetail updatePermissions(@PathVariable long roleId,
                                                                   @Valid @RequestBody OpsRolePermissionsUpdateRequest req) {
        return service.updatePermissions(roleId, req.permissionCodes());
    }

    @Operation(summary = "角色成员列表")
    @RequirePermission("ops.team.role.assign_member")
    @GetMapping("/{roleId}/members")
    public List<OpsTeamRoleAdminService.MemberRef> listMembers(@PathVariable long roleId) {
        return service.listMembers(roleId);
    }

    @Operation(summary = "全量替换成员（OPS_SUPER_ADMIN 仅限超管操作）")
    @RequirePermission("ops.team.role.assign_member")
    @Auditable(module = "ops", action = "分配成员", objectType = EntityTypes.SYS_ROLE, objectIdExpr = "#roleId")
    @PutMapping("/{roleId}/members")
    public ResponseEntity<Void> assignMembers(@PathVariable long roleId,
                                               @Valid @RequestBody OpsRoleMembersRequest req) {
        service.assignMembers(roleId, req.userIds());
        return ResponseEntity.noContent().build();
    }

}
