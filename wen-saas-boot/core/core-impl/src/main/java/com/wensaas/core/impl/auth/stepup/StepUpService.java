package com.wensaas.core.impl.auth.stepup;

import com.wensaas.core.api.auth.login.MfaMethod;

/**
 * 敏感操作 step-up（前置身份验证）服务。
 *
 * <p>统一「修改密码 / 手机号 / 邮箱」等敏感操作的身份复验：用户任选一条已配置通道
 * （TOTP / 短信 / 邮件）验证身份，通过后签发短时票据；敏感操作端点凭票据执行。
 *
 * <p>通道可用口径：短信=账号有手机号、邮件=账号有邮箱、TOTP=已绑定身份验证器
 * （当前生效 MFA 通道为 TOTP）。未配置的通道由前端置灰。
 *
 * <p>运营 / 租户账号共用同一套（按 {@code UserContext} 的 userId/tenantId 自助）。
 */
public interface StepUpService {

    /** 各通道对当前用户是否可用（供前端渲染置灰 + 展示掩码目标）。 */
    Channels availability(long userId);

    record Channels(ChannelAvail totp, ChannelAvail sms, ChannelAvail email) {
    }

    record ChannelAvail(boolean available, String maskedTarget) {
    }

    /** 发起挑战：短信 / 邮件发码到账号绑定手机 / 邮箱；TOTP 无需发码（前端直接输动态码）。 */
    Challenge challenge(long userId, long tenantId, MfaMethod channel);

    record Challenge(MfaMethod channel, String maskedTarget, int ttlSeconds, int resendAfterSeconds) {
    }

    /** 校验身份，通过则签发 step-up 票据。 */
    Ticket verify(long userId, long tenantId, MfaMethod channel, String code);

    record Ticket(String stepUpToken, long expiresInSeconds) {
    }

    /** 校验票据有效（不核销）——用于改手机 / 改邮箱的「发新目标验证码」中间步。 */
    void requireValid(long userId, String stepUpToken);

    /** 校验并核销票据——用于最终变更调用（改密码 / 改手机 / 改邮箱）。 */
    void consume(long userId, String stepUpToken);
}
