package com.wensaas.core.api.ops.team;

import com.wensaas.core.api.auth.oauth.OAuthBindingService;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
 * 运营团队成员管理。
 *
 * <p>仅服务运营账号（{@code tenant_id=0}）。简化为列表 + 无组织架构。
 * 调用方为 {@code /api/v1/ops/team/members/**}，由 {@code OpsAuthWebFilter} 限定 tenant=0。
 */
public interface OpsTeamMemberAdminService {

    /** 列表（关键字匹配 username/realName/email/mobile；status 0/1 筛选；按 created_at desc）。 */
    List<MemberListItem> listMembers(String keyword, Integer status);

    /** 详情（含角色 ID 列表）。 */
    MemberDetail getMember(long userId);

    /** 新建成员（不发邀请，超管线下传递凭据）。 */
    MemberDetail createMember(CreateMemberCommand cmd);

    /** 编辑（不改密码 / 用户名）。 */
    MemberDetail updateMember(long userId, UpdateMemberCommand cmd);

    /** 停用（status=0），不可停用自己。 */
    void disableMember(long userId, long currentUserId);

    /** 启用（status=1）。 */
    void enableMember(long userId);

    /** 直接置新密码（运营 hard-reset）；强度校验由 PasswordStrengthValidator。 */
    void resetPassword(long userId, String newPassword);

    /** 成员的活跃第三方账号绑定列表（运营成员常态仅企业 SSO，多为空）。 */
    List<OAuthBindingService.Binding> listOAuthBindings(long userId);

    /** 解绑 OAuth（{@code sys_user_oauth_binding} 删除一行）。 */
    void unbindOAuth(long userId, String provider);

    /**
     * 解密查看团队成员完整手机 / 邮箱（明文）。列表 / 详情默认脱敏，本方法是唯一返明文出口；
     * 控制层 {@code @RequirePermission("ops.team.member.contact.reveal")} + {@code @Auditable} 留痕。
     */
    ContactReveal revealContact(long userId);

    // ===== records =====

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

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class MemberListItem {
        private long id;
        private String username;
        private String realName;
        private String email;
        private String mobile;
        private int status;
        private List<String> roleCodes;
        private LocalDateTime lastLoginAt;
        private LocalDateTime createdAt;
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
        private String email;
        private String mobile;
        private Integer gender;
        private LocalDate birthday;
        private String empNo;
        private int status;
        private List<Long> roleIds;
        private List<String> roleCodes;
        private LocalDateTime lastLoginAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    record CreateMemberCommand(
            String username,
            String password,
            String realName,
            String email,
            String mobile,
            List<Long> roleIds
    ) {}

    record UpdateMemberCommand(
            String realName,
            String email,
            String mobile,
            Integer gender,
            LocalDate birthday,
            String empNo,
            List<Long> roleIds
    ) {}
}
