package com.wensaas.core.api.verification;

/**
 * 验证码业务异常（发送阶段）。校验阶段以 boolean 返回，不抛此异常。
 */
public class VerificationException extends RuntimeException {

    public enum Code {
        /** 距上次发送未到最小间隔。 */
        RATE_LIMITED,
        /** 当日发送次数超限。 */
        DAILY_LIMIT_EXCEEDED,
        /** 目标（手机号 / 邮箱）为空。 */
        TARGET_REQUIRED,
        /** 目标通道不可用（无适配器或收件人不满足该通道）。 */
        CHANNEL_UNAVAILABLE,
        /** 通道投递失败。 */
        DELIVERY_FAILED
    }

    private final Code code;
    /** RATE_LIMITED 时剩余秒数；其余为 null。 */
    private final Integer retryAfterSeconds;

    public VerificationException(Code code, String message) {
        this(code, message, null);
    }

    public VerificationException(Code code, String message, Integer retryAfterSeconds) {
        super(message);
        this.code = code;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public Code code() {
        return code;
    }

    public Integer retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
