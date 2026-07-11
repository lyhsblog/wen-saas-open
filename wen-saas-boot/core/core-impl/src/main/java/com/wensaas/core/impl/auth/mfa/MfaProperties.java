package com.wensaas.core.impl.auth.mfa;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;

/**
 * MFA 配置。
 *
 * <pre>
 * wen-saas-boot:
 *   mfa:
 *     issuer: Wen SaaS               # otpauth:// 的 issuer，展示在 Authenticator App
 *     recovery-code-count: 8
 *     pre-auth-ttl: 5m               # 登录后待二步验证的预认证票据有效期
 *     reconfigure-ttl: 10m           # 个人中心重新配置 MFA 的临时态有效期
 *     secret-key: ${WEN_SAAS_BOOT_MFA_KEY:dev-only-mfa-secret-change-in-prod}
 *     secret-salt: ${WEN_SAAS_BOOT_MFA_SALT:5c0744940b5c369b}   # hex
 * </pre>
 */
@ConfigurationProperties(prefix = "wen-saas-boot.mfa")
@Getter
@Setter
public class MfaProperties {

    private String issuer = "Wen SaaS";
    private int recoveryCodeCount = 8;
    private Duration preAuthTtl = Duration.ofMinutes(5);
    private Duration reconfigureTtl = Duration.ofMinutes(10);
    /** 敏感操作 step-up 验证票据有效期（验证通过后凭此票据完成改密码/改手机/改邮箱）。 */
    private Duration stepUpTtl = Duration.ofMinutes(10);
    /** TOTP 密钥加密口令（AES）。prod 必须由环境变量注入。 */
    private String secretKey = "dev-only-mfa-secret-change-in-prod";
    /** 加密盐（hex 字符串，偶数长度）。 */
    private String secretSalt = "5c0744940b5c369b";

}
