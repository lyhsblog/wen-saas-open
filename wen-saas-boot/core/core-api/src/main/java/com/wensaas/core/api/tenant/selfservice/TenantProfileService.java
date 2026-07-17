package com.wensaas.core.api.tenant.selfservice;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.OffsetDateTime;
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
 * 租户后台 · 个人中心。
 *
 * <p>面向租户内的普通成员/主管理员，管理自己的账号信息、安全设置与登录设备；
 * 调用方为 {@code /api/v1/base/profile/**}，主体取自 {@code UserContext}，
 * 在租户上下文内（{@code tenant_id != 0}）。与运营侧 {@code OpsProfileService} 平行。
 *
 * <p>与运营侧差异：MFA 为 opt-in（非强制，可自助关闭）；无团队/数据范围的强制 ALL；
 * 通知偏好不在个人中心（由通知模板渠道开关统一控制）。
 */
public interface TenantProfileService {

    // ===== 按 tab 拆分的只读视图 =====

    /** 基本信息 tab。 */
    Basic getBasic(long tenantId, long userId);

    /** 组织与角色 tab（只读：部门 / 角色 / 状态 / 数据范围）。{@code owner} 取自 UserPrincipal.tenantOwner。 */
    Work getWork(long tenantId, long userId, boolean owner);

    /** 安全设置 tab（只读：会话超时 / 锁定阈值 / MFA 状态）。 */
    Security getSecurity(long tenantId, long userId);

    // ===== 写操作 =====

    /** 修改可自助编辑的基本信息（性别 / 生日 / 头像）。姓名不可自助修改，仅管理员在用户管理处变更。 */
    void updateProfile(long tenantId, long userId, ProfileUpdate update);

    /** 上传头像：落文件中心 + 回写 {@code avatar_file_id}，返回新 fileId 与签名 URL。 */
    AvatarResult uploadAvatar(long tenantId, long userId, AvatarUpload upload);

    /**
     * 自助修改密码：身份已由 step-up（前置身份验证）证明 → 强度校验 → 写新哈希 →
     * 强制下线该用户全部会话（含当前）。失败抛 {@link TenantProfileException}。
     */
    void changePassword(long tenantId, long userId, String newPassword);

    /** 列出当前用户全部活跃会话，标记 {@code current}。 */
    List<SessionItem> listSessions(long tenantId, long userId, String currentSessionId);

    /** 下线指定会话；不可下线当前会话；不属于本人则 SESSION_NOT_FOUND。 */
    void revokeSession(long tenantId, long userId, String currentSessionId, String targetSessionId);

    /** 下线除当前会话外的全部会话。 */
    void revokeOtherSessions(long tenantId, long userId, String currentSessionId);

    // ===== 改手机号 · step-up 前置身份验证后（发码到新手机 → 校验并更新）=====

    MobileChangeStep sendNewMobileCode(long tenantId, long userId, String newMobile);

    void confirmNewMobile(long tenantId, long userId, String code);

    // ===== 改邮箱 · step-up 前置身份验证后（发码到新邮箱 → 校验唯一性并更新）=====

    /** 发码到新邮箱（调用方须先校验 step-up 票据有效）。 */
    MobileChangeStep sendNewEmailCode(long tenantId, long userId, String newEmail);

    /** 校验新邮箱码 → 租户内唯一性校验 → 更新 {@code sys_user.email}（调用方须已核销 step-up 票据）。 */
    void confirmNewEmail(long tenantId, long userId, String code);

    // ===== 视图记录 =====

    /**
     * 基本信息（自己的账号，返回完整手机/邮箱，由前端按需脱敏展示）。
     */
    record Basic(
            long userId,
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
     * 组织与角色（只读）。
     *
     * @param mainDeptName       主部门名称（可空）
     * @param mainDeptPath       主部门完整路径（含上级，以 " / " 连接；可空）
     * @param secondaryDeptNames 兼任部门名称
     * @param roles              平台角色
     * @param statusLabel        账号状态（active / disabled）
     * @param dataScopeLabel     数据范围中文描述（取最宽角色）
     * @param owner              是否主管理员
     */
    record Work(
            String mainDeptName,
            String mainDeptPath,
            List<String> secondaryDeptNames,
            List<RoleBrief> roles,
            String statusLabel,
            String dataScopeLabel,
            boolean owner) {
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
        private String code;
        private String name;

    }

    /**
     * 安全设置（只读视图）。
     *
     * @param tokenTtlSeconds 会话超时（秒）
     * @param maxAttempts     登录失败锁定阈值（次）
     * @param windowSeconds   锁定窗口（秒）
     * @param mfaRequired     是否被平台强制登录二步验证（租户恒 false）
     * @param mfaEnabled      登录二步验证开关
     * @param mfaMethod       推导的登录验证通道（TOTP/EMAIL；未开启为 null）
     */
    record Security(
            long tokenTtlSeconds,
            int maxAttempts,
            int windowSeconds,
            boolean mfaRequired,
            boolean mfaEnabled,
            String mfaMethod) {
    }

    /**
     * 一个活跃会话/设备。
     *
     * @param deviceLabel 设备标签「浏览器 · 系统」（解析自 User-Agent），无法识别为「未知设备」
     * @param ip          登录时客户端 IP，可能为 null（历史会话 / 无请求上下文）
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class SessionItem {
        private String sessionId;
        private OffsetDateTime loginAt;
        private OffsetDateTime lastAccessAt;
        private boolean current;
        private String deviceLabel;
        private String ip;

    }

    /** 改手机号每一步发码结果（脱敏目标 + TTL + 重发间隔）。 */
    record MobileChangeStep(String maskedTarget, int ttlSeconds, int resendAfterSeconds) {
    }

    /** 可自助编辑的基本信息（不含姓名：姓名仅管理员可改）。 */
    record ProfileUpdate(Integer gender, LocalDate birthday, String avatarFileId) {
    }

    /** 头像上传入参。 */
    record AvatarUpload(String fileName, String mimeType, long sizeBytes, InputStream content) {
    }

    /** 头像上传结果。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class AvatarResult {
        private String avatarFileId;
        private String avatarUrl;

    }
}
