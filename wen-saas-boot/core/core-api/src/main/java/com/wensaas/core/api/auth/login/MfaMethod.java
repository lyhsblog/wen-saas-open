package com.wensaas.core.api.auth.login;

/**
 * MFA 二步验证通道（安全设置中用户可选的验证方式）。
 */
public enum MfaMethod {
    /** Authenticator App（TOTP，RFC 6238）。 */
    TOTP,
    /** 短信动态码。 */
    SMS,
    /** 邮箱动态码。 */
    EMAIL
}
