package com.wensaas.core.api.auth.permission;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.wensaas.core.api.auth.oauth.OAuthBindingService;
import com.wensaas.core.api.namesource.EntityTypes;
import com.wensaas.core.api.namesource.TransDescend;
import com.wensaas.core.api.namesource.TransField;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 成员管理（写）API。
 *
 * <p>租户上下文：所有方法从 {@code TenantContext.require()} 取 {@code tenantId}，
 * MP {@code TenantLineInnerInterceptor} 自动注入 {@code tenant_id} 过滤。
 *
 * <p>主管理员（{@code sys_tenant.main_admin_user_id}）受保护：不可停用、不可被他人调部门。
 * 账号一旦创建不可修改；成员不做物理删除，仅停用。
 *
 * <p>新增成员不设初始密码：签发 48h 激活链接（复用 {@code PasswordResetTokenService}，
 * purpose={@code reset_member}），管理员复制传递或发送到成员手机/邮箱。
 *
 * <p>手机 / 邮箱为敏感字段，落库确定性 AES 加密；唯一性校验走密文等值。
 */
public interface MemberAdminService {

    /** 分页列出成员（部门过滤 / 含子部门 / 关键字 / 状态 / 角色筛选）。 */
    MemberPage listMembers(MemberQuery query);

    /** 成员详情（含部门主兼、角色、登录信息）。 */
    MemberDetail getMember(long userId);

    /** 新建成员（生成激活链接，不设初始密码）。 */
    CreatedMember createMember(CreateMemberCommand cmd);

    /** 编辑成员（不改账号）。{@code roleIds}/{@code deptBindings} 为 null 时不动关联。 */
    MemberDetail updateMember(long userId, UpdateMemberCommand cmd);

    /** 重新生成激活 / 重置密码链接（48h）。 */
    IssuedActivation reissueActivation(long userId);

    /**
     * 停用 / 启用。停用即下线全部会话；主管理员 / 自己不可停用。
     *
     * <p>停用是<b>状态信号</b>而非连锁清理（产品既定决策）：角色、部门负责人身份、
     * 创建的数据均保留，恢复启用后立即复权、无需重新配置。被停用成员仍保留的部门负责人身份由
     * {@link MemberDetail#leaderDepts()} / {@link MemberListItem#leaderDeptCount()} 暴露给前端高亮提醒，
     * 主管理员按需手动改派（不自动卸任）。
     */
    void setStatus(long userId, boolean enabled, long currentUserId);

    /** 全量替换成员角色。 */
    MemberDetail assignRoles(long userId, List<Long> roleIds);

    /** 管理所属部门：设置主部门 + 兼任部门集合（至少保留 1 个部门）。 */
    MemberDetail manageDepts(long userId, ManageDeptsCommand cmd);

    /** 解除登录失败锁定。 */
    void unlockLogin(long userId);

    /** 成员检索（供角色分配等场景，按 username/realName 匹配）。 */
    List<MemberSearchItem> search(String keyword, int limit);

    /**
     * 成员第三方账号绑定列表（成员详情「登录信息」Tab 展示）。
     * 复用 {@link OAuthBindingService#listByUser(long, long)}，按当前租户隔离。
     */
    List<OAuthBindingService.Binding> listOAuthBindings(long userId);

    /**
     * 成员最近登录记录（来自 {@code sys_login_log}，按时间倒序取 {@code limit} 条；含近 180 天内）。
     * 供成员详情「登录信息」Tab 展示登录 IP / 设备 / 成功失败明细。
     */
    List<LoginLogItem> listLoginLogs(long userId, int limit);

    /**
     * 解密查看成员完整手机 / 邮箱（明文）。列表 / 详情默认 {@code Sensitives} 掩码，
     * 本方法是唯一返明文出口；控制层以 {@code @RequirePermission("org.member.contact.reveal")}
     * 门控 + {@code @Auditable} 留痕（手机 / 邮箱属敏感字段）。
     */
    ContactReveal revealContact(long userId);

    // ===================== query / command =====================

    record MemberQuery(
            Long deptId,
            boolean includeSubDepts,
            String keyword,
            Integer status,
            Long roleId,
            int page,
            int pageSize) {
    }

    record CreateMemberCommand(
            String username,
            String realName,
            String mobile,
            String email,
            Long mainDeptId,
            List<Long> secondaryDeptIds,
            List<Long> roleIds,
            String positionCode,
            String remark) {
    }

    record UpdateMemberCommand(
            String realName,
            String mobile,
            String email,
            Integer gender,
            LocalDate birthday,
            String empNo,
            String positionCode,
            List<Long> roleIds) {
    }

    record ManageDeptsCommand(
            Long mainDeptId,
            List<Long> secondaryDeptIds) {
    }

    // ===================== views =====================

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class MemberPage {
        private List<MemberListItem> items;
        private long total;
        private int page;
        private int pageSize;

    }

    /**
     * 成员列表项。可变 Lombok POJO（fluent 访问器保持 record 风格 {@code field()} 调用、方法引用兼容），
     * 名字段经 {@code TransFiller.fill} 反射回填。{@code @JsonAutoDetect} 走字段序列化，
     * 保证与原 record 的 JSON 结构一致。
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY,
            getterVisibility = JsonAutoDetect.Visibility.NONE,
            isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    final class MemberListItem {
        long id;
        String username;
        String realName;
        String mobile;
        String email;
        long mainDeptId;
        @TransField(type = EntityTypes.SYS_DEPT, ref = "mainDeptId")
        String mainDeptName;
        int secondaryDeptCount;
        int status;
        boolean owner;
        List<Long> roleIds;
        @TransField(type = EntityTypes.SYS_ROLE, ref = "roleIds")
        List<String> roleNames;
        String positionCode;
        String positionName;
        boolean loginLocked;
        LocalDateTime lastLoginAt;
        LocalDateTime createdAt;
        int leaderDeptCount;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class MemberDetail {
        private long id;
        private String username;
        private String realName;
        private String mobile;
        private String email;
        private Integer gender;
        private LocalDate birthday;
        private String empNo;
        private String positionCode;
        private String positionName;
        private int status;
        private boolean owner;
        private boolean loginLocked;
        private long lockRemainingSeconds;
        @TransDescend
        private DeptBinding mainDept;
        @TransDescend
        private List<DeptBinding> secondaryDepts;
        private List<Long> roleIds;
        private List<RoleBrief> roles;
        private LocalDateTime lastLoginAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        @TransDescend
        private List<DeptBinding> leaderDepts;

    }

    /**
     * 部门绑定项（id + 名）。可变 Lombok POJO，{@code deptName} 经 {@code TransFiller.fill} 回填；
     * 外层 {@code MemberDetail} 仍可为 record，fill 会深递归进本对象回填。
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY,
            getterVisibility = JsonAutoDetect.Visibility.NONE,
            isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    final class DeptBinding {
        long deptId;
        @TransField(type = EntityTypes.SYS_DEPT, ref = "deptId")
        String deptName;

        public DeptBinding(long deptId) {
            this.deptId = deptId;
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class RoleBrief {
        private long roleId;
        private String code;
        private String name;

    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class MemberSearchItem {
        private long id;
        private String username;
        private String realName;
        private String mainDeptName;

    }

    /**
     * 一条登录记录（{@code sys_login_log} 精简投影）。
     *
     * @param loginMethod password / sms / wechat / wecom / dingtalk
     * @param action      login / logout / session_expired
     * @param result      success / fail
     * @param failReason  bad_credentials / disabled / locked / oauth_failed / other（成功为 null）
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class LoginLogItem {
        private String loginMethod;
        private String action;
        private String result;
        private String failReason;
        private String ip;
        private String userAgent;
        private LocalDateTime createdAt;

    }

    /** 解密后的完整联系方式（明文）。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class ContactReveal {
        private String mobile;
        private String email;

    }

    record CreatedMember(long id, String username, String activationToken, Instant activationExpiresAt) {
    }

    record IssuedActivation(String activationToken, Instant activationExpiresAt, String username) {
    }
}
