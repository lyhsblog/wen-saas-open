package com.wensaas.core.api.ops;

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
 * 运营成员个人中心聚合视图。基本信息 + 团队角色(只读) + 安全设置(只读) + 企业 SSO(占位)。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public class OpsProfile {
    private Basic basic;
    private TeamsRoles teamsRoles;
    private Security security;
    private Sso sso;

    /**
     * 基本信息。{@code username/empNo/email} 成员只读（由超管设定）；
     * {@code realName/gender/birthday/avatar} 可自助修改；{@code mobile} 敏感（需双通道验证，暂不在本期）。
     */
    public record Basic(
            Long userId,
            String username,
            String empNo,
            String email,
            String realName,
            String mobile,
            Integer gender,
            LocalDate birthday,
            String avatarFileId,
            String avatarUrl) {
    }

    /**
     * 团队与角色（只读）。仅保留有真实数据来源的字段：
     * {@code roles}（sys_user_role × sys_role）、{@code dataScope}（运营固定"全部数据"）、
     * {@code accountStatus}（sys_user.status）。所属团队 / 职位 / 入职日期 / 直属上级因运营后台不建组织树、
     * 团队成员页未建模，不展示（如未来建模再行补充）。
     */
    public record TeamsRoles(
            List<RoleBrief> roles,
            String dataScope,
            String accountStatus) {
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    public static class RoleBrief {
        private String code;
        private String name;

    }

    /** 安全设置（只读）。会话超时 / 登录锁定阈值来源于系统参数。 */
    public record Security(
            long sessionTimeoutSeconds,
            int loginLockMaxAttempts,
            int loginLockWindowSeconds,
            /** 是否被平台强制登录二步验证（auth.ops-mfa-enforced 配置）。 */
            boolean mfaRequired,
            /** 登录二步验证开关。 */
            boolean mfaEnabled,
            /** 推导的登录验证通道（TOTP/EMAIL）；未开启为 null。 */
            String mfaMethod) {
    }

    /** 企业 SSO（只读占位；当前无 SSO 系统）。 */
    public record Sso(
            String provider,
            boolean enabled,
            LocalDateTime lastSyncAt) {
    }
}
