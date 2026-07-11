package com.wensaas.core.api.ops;

import java.io.InputStream;
import java.time.LocalDate;
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
 * 运营后台个人中心服务（不含 MFA）。
 *
 * <p>仅服务运营账号（{@code tenant_id=0}）；调用方为 {@code /api/v1/ops/profile/**}，
 * 已由 {@code OpsAuthWebFilter} 限定为运营账号。所有 DB 操作绕过 MP TenantLine（显式 tenant_id）。
 */
public interface OpsProfileService {

    // ===== 个人中心按 tab 拆分的只读视图 =====
    // 前端"点 tab 才拉该 tab 数据"，保证切换时信息实时，不再一次性聚合下发。

    /** 基本信息 tab。 */
    OpsProfile.Basic getBasic(long tenantId, long userId);

    /** 团队与角色 tab（只读）。 */
    OpsProfile.TeamsRoles getTeamsRoles(long tenantId, long userId);

    /** 安全设置 tab（只读）。 */
    OpsProfile.Security getSecurity(long tenantId, long userId);

    /** 企业 SSO tab（只读占位）。 */
    OpsProfile.Sso getSso(long tenantId, long userId);

    /** 修改可自助编辑的基本信息（性别 / 生日 / 头像）。姓名不可自助修改，仅运营管理员在运营成员管理处变更。 */
    void updateProfile(long tenantId, long userId, ProfileUpdate update);

    /** 上传头像（≤1MB，JPG/PNG）：落文件中心并回写 {@code avatar_file_id}，返回新 fileId + 签名 URL。 */
    AvatarResult uploadAvatar(long tenantId, long userId, AvatarUpload upload);

    /**
     * 自助修改密码：身份已由 step-up（前置身份验证）证明 → 强度校验 → 写新哈希 →
     * 强制下线该用户全部会话（含当前）。失败抛 {@link OpsProfileException}。
     */
    void changePassword(long tenantId, long userId, String newPassword);

    /** 列出当前用户全部活跃会话，标记 {@code current}。 */
    List<OpsSessionItem> listSessions(long tenantId, long userId, String currentSessionId);

    /** 下线指定会话；不可下线当前会话（抛 CANNOT_REVOKE_CURRENT_SESSION）；不属于本人则 SESSION_NOT_FOUND。 */
    void revokeSession(long tenantId, long userId, String currentSessionId, String targetSessionId);

    /** 下线除当前会话外的全部会话。 */
    void revokeOtherSessions(long tenantId, long userId, String currentSessionId);

    /** 分页查询当前用户登录日志（按 created_at 倒序）。 */
    OpsLoginLogPage listLoginLogs(long tenantId, long userId, int page, int size);

    // ===== 改手机号 · step-up 前置身份验证后 =====
    // 身份复验由 StepUpService 统一完成；此处只负责「发码到新手机 → 校验并更新」。

    /** 发码到新手机（调用方须先校验 step-up 票据有效）。 */
    MobileChangeStep sendNewMobileCode(long tenantId, long userId, String newMobile);

    /** 校验新手机码 → 更新 {@code sys_user.mobile}（调用方须已核销 step-up 票据）。 */
    void confirmNewMobile(long tenantId, long userId, String code);

    /** 改手机号每一步发码的结果（脱敏目标 + TTL + 重发间隔，前端展示倒计时）。 */
    record MobileChangeStep(String maskedTarget, int ttlSeconds, int resendAfterSeconds) {
    }

    /**
     * 可自助编辑的基本信息（不含姓名：姓名仅运营管理员可改）。
     *
     * @param gender       性别 0/1/2（可空）
     * @param birthday     生日（可空）
     * @param avatarFileId 头像文件 ID（可空，来自文件上传）
     */
    record ProfileUpdate(Integer gender, LocalDate birthday, String avatarFileId) {
    }

    /** 头像上传入参。 */
    record AvatarUpload(String fileName, String mimeType, long sizeBytes, InputStream content) {
    }

    /** 头像上传结果（新文件 ID + 短期签名 URL）。 */
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

    /** 登录日志分页结果。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class OpsLoginLogPage {
        private List<OpsLoginLogItem> records;
        private long total;

    }
}
