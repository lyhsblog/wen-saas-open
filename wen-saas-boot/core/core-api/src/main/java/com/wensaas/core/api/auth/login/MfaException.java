package com.wensaas.core.api.auth.login;

/**
 * MFA 业务异常。
 */
public class MfaException extends RuntimeException {

    public enum Code {
        /** 预认证票据无效 / 已过期（需重新登录）。 */
        PREAUTH_INVALID,
        /** 验证码 / TOTP / 恢复码错误。 */
        CODE_INVALID,
        /** 未选择 / 不支持的 MFA 通道。 */
        METHOD_REQUIRED,
        /** 尚未配置 MFA。 */
        NOT_CONFIGURED,
        /** 该通道缺少目标（手机号 / 邮箱未设置）。 */
        TARGET_MISSING,
        /** 当前不处于待校验状态（如未先发起 setup / challenge）。 */
        ILLEGAL_STATE
    }

    private final Code code;

    public MfaException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
