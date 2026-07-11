package com.wensaas.core.web.ops;

import com.wensaas.core.api.namesource.EntityTypes;
import com.wensaas.core.api.audit.Auditable;
import com.wensaas.core.api.auth.permission.RequirePermission;
import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.auth.user.UserPrincipal;
import com.wensaas.core.api.ops.team.OpsTeamMemberAdminService;
import com.wensaas.core.web.ops.dto.OpsMemberCreateRequest;
import com.wensaas.core.web.ops.dto.OpsMemberResetPasswordRequest;
import com.wensaas.core.web.ops.dto.OpsMemberUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
 * 运营后台 · 团队成员管理。
 *
 * <p>超管保护：目标成员持有 OPS_SUPER_ADMIN 时，编辑 / 重置密码 / 停启用 / 解绑 OAuth
 * 仅限超管本人操作；授予 OPS_SUPER_ADMIN 角色同样仅限超管。
 */
@Tag(name = "ops-team-member", description = "运营团队成员")
@SecurityRequirement(name = "bearer-jwt")
@RestController
@RequestMapping("/api/v1/ops/team/members")
public class OpsTeamMemberController {

    private final OpsTeamMemberAdminService service;

    public OpsTeamMemberController(OpsTeamMemberAdminService service) {
        this.service = service;
    }

    @Operation(summary = "成员列表（关键字 / 状态过滤）")
    @RequirePermission("ops.team.member.list")
    @GetMapping
    public List<OpsTeamMemberAdminService.MemberListItem> list(
            @Parameter(description = "关键字（username/realName/email/mobile 模糊）", required = false)
            @RequestParam(value = "keyword", required = false) String keyword,
            @Parameter(description = "0=停用 / 1=启用 / 空=不筛选", required = false)
            @RequestParam(value = "status", required = false) Integer status) {
        return service.listMembers(keyword, status);
    }

    @Operation(summary = "成员详情")
    @RequirePermission("ops.team.member.list")
    @GetMapping("/{userId}")
    public OpsTeamMemberAdminService.MemberDetail get(@PathVariable long userId) {
        return service.getMember(userId);
    }

    @Operation(summary = "解密查看成员完整联系方式",
            description = "列表/详情默认脱敏；本端点返明文手机/邮箱，需 ops.team.member.contact.reveal 权限并留审计")
    @RequirePermission("ops.team.member.contact.reveal")
    @Auditable(module = "ops", action = "查看联系方式", objectType = EntityTypes.SYS_USER,
            objectIdExpr = "#userId")
    @GetMapping("/{userId}/contact")
    public OpsTeamMemberAdminService.ContactReveal revealContact(@PathVariable long userId) {
        return service.revealContact(userId);
    }

    @Operation(summary = "新建成员")
    @RequirePermission("ops.team.member.create")
    @Auditable(module = "ops", action = "新建成员", objectType = EntityTypes.SYS_USER,
            objectIdExpr = "#result.id()", objectDescExpr = "#result.realName()")
    @PostMapping
    public OpsTeamMemberAdminService.MemberDetail create(@Valid @RequestBody OpsMemberCreateRequest req) {
        return service.createMember(new OpsTeamMemberAdminService.CreateMemberCommand(
                req.username(), req.password(), req.realName(),
                req.email(), req.mobile(), req.roleIds()));
    }

    @Operation(summary = "编辑成员")
    @RequirePermission("ops.team.member.update")
    @Auditable(module = "ops", action = "编辑成员", objectType = EntityTypes.SYS_USER,
            objectIdExpr = "#userId")
    @PutMapping("/{userId}")
    public OpsTeamMemberAdminService.MemberDetail update(@PathVariable long userId,
                                                          @Valid @RequestBody OpsMemberUpdateRequest req) {
        return service.updateMember(userId, new OpsTeamMemberAdminService.UpdateMemberCommand(
                req.realName(), req.email(), req.mobile(),
                req.gender(), req.birthday(), req.empNo(), req.roleIds()));
    }

    @Operation(summary = "停用（不可停用自己）")
    @RequirePermission("ops.team.member.disable")
    @Auditable(module = "ops", action = "停用成员", objectType = EntityTypes.SYS_USER, objectIdExpr = "#userId")
    @PostMapping("/{userId}/disable")
    public ResponseEntity<Void> disable(@PathVariable long userId) {
        UserPrincipal me = UserContext.get();
        service.disableMember(userId, me == null ? -1L : me.userId());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "启用")
    @RequirePermission("ops.team.member.disable")
    @Auditable(module = "ops", action = "启用成员", objectType = EntityTypes.SYS_USER, objectIdExpr = "#userId")
    @PostMapping("/{userId}/enable")
    public ResponseEntity<Void> enable(@PathVariable long userId) {
        service.enableMember(userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "重置密码（hard-reset；会下线该用户全部会话）")
    @RequirePermission("ops.team.member.reset_password")
    @Auditable(module = "ops", action = "重置密码", objectType = EntityTypes.SYS_USER, objectIdExpr = "#userId")
    @PostMapping("/{userId}/reset-password")
    public ResponseEntity<Void> resetPassword(@PathVariable long userId,
                                               @Valid @RequestBody OpsMemberResetPasswordRequest req) {
        service.resetPassword(userId, req.newPassword());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "成员第三方账号绑定列表",
            description = "运营成员常态仅企业 SSO，多为空；用于解绑前查看")
    @RequirePermission("ops.team.member.list")
    @GetMapping("/{userId}/oauth-bindings")
    public List<com.wensaas.core.api.auth.oauth.OAuthBindingService.Binding> listOAuthBindings(
            @PathVariable long userId) {
        return service.listOAuthBindings(userId);
    }

    @Operation(summary = "解绑 OAuth")
    @RequirePermission("ops.team.member.unbind_oauth")
    @Auditable(module = "ops", action = "解绑第三方账号", objectType = EntityTypes.SYS_USER, objectIdExpr = "#userId")
    @DeleteMapping("/{userId}/oauth-bindings/{provider}")
    public ResponseEntity<Void> unbindOAuth(@PathVariable long userId, @PathVariable String provider) {
        service.unbindOAuth(userId, provider);
        return ResponseEntity.noContent().build();
    }
}
