package com.wensaas.core.web.member;

import com.wensaas.core.api.namesource.EntityTypes;
import com.wensaas.core.api.audit.Auditable;
import com.wensaas.core.api.auth.permission.MemberAdminService;
import com.wensaas.core.api.auth.permission.RequirePermission;
import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.auth.user.UserPrincipal;
import com.wensaas.core.api.dict.DictService;
import com.wensaas.core.web.common.PageQuery;
import com.wensaas.core.web.member.dto.MemberRequests;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
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
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * 成员管理（租户后台 · 通讯录）。
 */
@Tag(name = "tenant-member", description = "租户后台 · 组织与成员")
@SecurityRequirement(name = "bearer-jwt")
@RestController
@RequestMapping("/api/v1/base/members")
public class MemberAdminController {

    private final MemberAdminService service;
    private final DictService dictService;

    public MemberAdminController(MemberAdminService service, DictService dictService) {
        this.service = service;
        this.dictService = dictService;
    }

    @Operation(summary = "分页列出成员",
            description = "deptId+includeSubDepts 按部门过滤；keyword 匹配账号/姓名/手机/邮箱；status/roleId 筛选")
    @RequirePermission("org.member.list")
    @GetMapping
    public MemberAdminService.MemberPage list(
            @RequestParam(required = false) Long deptId,
            @RequestParam(defaultValue = "false") boolean includeSubDepts,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long roleId,
            @Valid @ParameterObject PageQuery pageQuery) {
        return service.listMembers(new MemberAdminService.MemberQuery(
                deptId, includeSubDepts, keyword, status, roleId, pageQuery.page(), pageQuery.size()));
    }

    @Operation(summary = "成员检索（供角色分配等）")
    @RequirePermission("org.member.list")
    @GetMapping("/search")
    public List<MemberAdminService.MemberSearchItem> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "20") int limit) {
        return service.search(keyword, limit);
    }

    @Operation(summary = "成员详情")
    @RequirePermission("org.member.list")
    @GetMapping("/{userId}")
    public MemberAdminService.MemberDetail detail(@PathVariable long userId) {
        return service.getMember(userId);
    }

    @Operation(summary = "岗位可选项（字典 POSITION）",
            description = "成员表单岗位下拉用；用 org.member.list 鉴权（编辑成员者无需字典管理权限）；"
                    + "新租户默认空，由租户在字典管理维护")
    @RequirePermission("org.member.list")
    @GetMapping("/positions")
    public List<PositionOption> positions() {
        return dictService.listItems("POSITION").stream()
                .map(i -> new PositionOption(i.code(), i.name()))
                .toList();
    }

    /** 岗位选项：code 入库、name 展示。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    public static class PositionOption {
        private String code;
        private String name;
    }

    @Operation(summary = "成员第三方账号绑定列表",
            description = "成员详情「登录信息」展示已绑定的钉钉 / 企微 / 微信账号；用 org.member.list 鉴权")
    @RequirePermission("org.member.list")
    @GetMapping("/{userId}/oauth-bindings")
    public List<com.wensaas.core.api.auth.oauth.OAuthBindingService.Binding> oauthBindings(
            @PathVariable long userId) {
        return service.listOAuthBindings(userId);
    }

    @Operation(summary = "成员最近登录记录",
            description = "成员详情「登录信息」展示登录 IP / 设备 / 成功失败明细；近 180 天内，倒序，limit≤100；用 org.member.list 鉴权")
    @RequirePermission("org.member.list")
    @GetMapping("/{userId}/login-logs")
    public List<MemberAdminService.LoginLogItem> loginLogs(
            @PathVariable long userId,
            @RequestParam(defaultValue = "20") int limit) {
        return service.listLoginLogs(userId, limit);
    }

    @Operation(summary = "解密查看成员完整联系方式",
            description = "列表/详情默认脱敏；本端点返明文手机/邮箱，需 org.member.contact.reveal 权限并留审计")
    @RequirePermission("org.member.contact.reveal")
    @Auditable(module = "__core__", action = "查看联系方式", objectType = EntityTypes.SYS_USER,
            objectIdExpr = "#userId")
    @GetMapping("/{userId}/contact")
    public MemberAdminService.ContactReveal revealContact(@PathVariable long userId) {
        return service.revealContact(userId);
    }

    @Operation(summary = "新建成员",
            description = "不设初始密码，返回 48h 激活链接 token；管理员复制传递或发送给成员")
    @RequirePermission("org.member.create")
    @Auditable(module = "__core__", action = "新建成员", objectType = EntityTypes.SYS_USER,
            objectIdExpr = "#result.id()", objectDescExpr = "#req.realName()")
    @PostMapping
    public MemberAdminService.CreatedMember create(@Valid @RequestBody MemberRequests.CreateRequest req) {
        return service.createMember(new MemberAdminService.CreateMemberCommand(
                req.username(), req.realName(), req.mobile(), req.email(),
                req.mainDeptId(), req.secondaryDeptIds(), req.roleIds(), req.positionCode(), req.remark()));
    }

    @Operation(summary = "编辑成员（不改账号）")
    @RequirePermission("org.member.update")
    @Auditable(module = "__core__", action = "编辑成员", objectType = EntityTypes.SYS_USER,
            objectIdExpr = "#userId")
    @PutMapping("/{userId}")
    public MemberAdminService.MemberDetail update(@PathVariable long userId,
                                                  @Valid @RequestBody MemberRequests.UpdateRequest req) {
        return service.updateMember(userId, new MemberAdminService.UpdateMemberCommand(
                req.realName(), req.mobile(), req.email(), req.gender(), req.birthday(), req.empNo(),
                req.positionCode(), req.roleIds()));
    }

    @Operation(summary = "设置成员角色（全量替换）")
    @RequirePermission("org.member.assign_role")
    @Auditable(module = "__core__", action = "分配角色", objectType = EntityTypes.SYS_USER,
            objectIdExpr = "#userId", objectDescExpr = "#result.realName()")
    @PutMapping("/{userId}/roles")
    public MemberAdminService.MemberDetail assignRoles(@PathVariable long userId,
                                                       @RequestBody MemberRequests.RolesRequest req) {
        return service.assignRoles(userId, req.roleIds());
    }

    @Operation(summary = "管理所属部门（主部门 + 兼任部门）")
    @RequirePermission("org.member.assign_dept")
    @Auditable(module = "__core__", action = "管理成员所属部门", objectType = EntityTypes.SYS_USER,
            objectIdExpr = "#userId", objectDescExpr = "#result.realName()")
    @PutMapping("/{userId}/depts")
    public MemberAdminService.MemberDetail manageDepts(@PathVariable long userId,
                                                       @Valid @RequestBody MemberRequests.DeptsRequest req) {
        return service.manageDepts(userId, new MemberAdminService.ManageDeptsCommand(
                req.mainDeptId(), req.secondaryDeptIds()));
    }

    @Operation(summary = "停用 / 启用成员")
    @RequirePermission("org.member.toggle_status")
    @Auditable(module = "__core__", action = "切换成员状态", objectType = EntityTypes.SYS_USER,
            objectIdExpr = "#userId")
    @PutMapping("/{userId}/status")
    public ResponseEntity<Void> setStatus(@PathVariable long userId, @RequestParam boolean enabled) {
        service.setStatus(userId, enabled, currentUserId());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "重新生成激活 / 重置密码链接（48h）")
    @RequirePermission("org.member.reset_password")
    // 对象名（成员姓名）由 service 内 AuditObjects.add 登记：result(IssuedActivation) 不含 realName。
    @Auditable(module = "__core__", action = "重发密码链接", objectType = EntityTypes.SYS_USER,
            objectIdExpr = "#userId")
    @PostMapping("/{userId}/reset-link")
    public MemberAdminService.IssuedActivation reissue(@PathVariable long userId) {
        return service.reissueActivation(userId);
    }

    @Operation(summary = "解除登录失败锁定")
    @RequirePermission("org.member.reset_password")
    @Auditable(module = "__core__", action = "解除登录锁定", objectType = EntityTypes.SYS_USER,
            objectIdExpr = "#userId")
    @DeleteMapping("/{userId}/login-lock")
    public ResponseEntity<Void> unlockLogin(@PathVariable long userId) {
        service.unlockLogin(userId);
        return ResponseEntity.noContent().build();
    }

    private static long currentUserId() {
        UserPrincipal p = UserContext.get();
        return p == null ? -1L : p.userId();
    }
}
