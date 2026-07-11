package com.wensaas.core.api.verification;

/**
 * 验证码用途。用于隔离不同业务场景的码（同一手机号 MFA 登录码与改手机旧机码互不干扰），
 * 也作为 Redis key 的一段。
 */
public enum VerificationPurpose {
    /** MFA 首次配置（短信 / 邮箱通道）。 */
    MFA_SETUP,
    /** MFA 登录挑战（短信 / 邮箱通道）。 */
    MFA_LOGIN,
    /** 改手机号 · 验证旧手机。 */
    PHONE_CHANGE_OLD,
    /** 改手机号 · 验证新手机。 */
    PHONE_CHANGE_NEW,
    /** 改邮箱 · 验证新邮箱。 */
    EMAIL_CHANGE_NEW,
    /** 敏感操作前置身份验证（step-up）· 短信 / 邮箱通道发码。 */
    STEP_UP,
    /** 改手机号 · 邮箱二次确认（旧双通道流已下线，保留枚举不破坏历史 Redis key）。 */
    EMAIL_CONFIRM
}
