package com.wensaas.core.impl.verification;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;

/**
 * 验证码（OTP）配置。
 *
 * <pre>
 * wen-saas-boot:
 *   verification:
 *     code-length: 6
 *     ttl: 5m
 *     resend-interval: 60s
 *     daily-limit: 10
 *     max-verify-attempts: 5
 * </pre>
 */
@ConfigurationProperties(prefix = "wen-saas-boot.verification")
@Getter
@Setter
public class VerificationCodeProperties {

    /** 验证码位数（纯数字）。 */
    private int codeLength = 6;
    /** 有效期。 */
    private Duration ttl = Duration.ofMinutes(5);
    /** 两次发送的最小间隔。 */
    private Duration resendInterval = Duration.ofSeconds(60);
    /** 同一 purpose+target 当日发送上限。 */
    private int dailyLimit = 10;
    /** 单个码允许的最大校验失败次数（达到即作废）。 */
    private int maxVerifyAttempts = 5;

}
