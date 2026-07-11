package com.wensaas.core.api.auth.login;

/**
 * 密码重置（忘记密码 token 链路）业务异常。
 */
public class PasswordResetException extends RuntimeException {

    public enum Code {
        TOKEN_NOT_FOUND,
        TOKEN_EXPIRED,
        TOKEN_USED,
        TOKEN_INVALIDATED,
        USERNAME_MISMATCH,
        PASSWORD_TOO_WEAK
    }

    private final Code code;

    public PasswordResetException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
