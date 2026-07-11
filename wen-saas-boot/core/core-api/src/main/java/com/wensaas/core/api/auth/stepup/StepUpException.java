package com.wensaas.core.api.auth.stepup;

/**
 * 敏感操作 step-up（前置身份验证）业务异常。
 */
public class StepUpException extends RuntimeException {

    public enum Code {
        /** step-up 票据无效 / 已过期（需重新验证身份）。 */
        TICKET_INVALID,
        /** 验证码 / 动态码错误。 */
        CODE_INVALID,
        /** 未选择 / 不支持的验证通道。 */
        METHOD_REQUIRED,
        /** 所选通道不可用（TOTP 未绑定，或账号未设置手机号 / 邮箱）。 */
        CHANNEL_UNAVAILABLE
    }

    private final Code code;

    public StepUpException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
