package com.wensaas.core.api.auth.login;

/**
 * 代登录相关业务异常。
 */
public class ImpersonateException extends RuntimeException {

    public enum Code {
        IMPERSONATE_TARGET_TENANT_NOT_FOUND,
        IMPERSONATE_TARGET_TENANT_INACTIVE,
        IMPERSONATE_TARGET_USER_NOT_FOUND,
        IMPERSONATE_USER_NOT_IN_TENANT,
        IMPERSONATE_TARGET_USER_DISABLED,
        IMPERSONATE_TARGET_IS_OPS,
        /** 运营数据范围拒绝：OPS_OWNED 代登录非名下（csm_user_id != self）租户 → 403 */
        IMPERSONATE_TENANT_NOT_IN_SCOPE,
        IMPERSONATE_NESTED_NOT_ALLOWED,
        IMPERSONATE_NONCE_INVALID,
        IMPERSONATE_OPERATOR_SESSION_GONE,
        EXIT_IMPERSONATION_NOT_IMPERSONATING,
        EXIT_IMPERSONATION_ORIGINAL_GONE
    }

    private final Code code;

    public ImpersonateException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
