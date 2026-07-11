package com.wensaas.core.api.auth.permission;

/**
 * 租户侧推荐角色启用领域异常。
 */
public class TenantRecommendedRoleException extends RuntimeException {

    public enum Code {
        TEMPLATE_NOT_FOUND,
        TEMPLATE_NOT_PUBLISHED,
        PACKAGE_NOT_MATCHED,
        ROLE_NAME_DUPLICATE,
        INVALID_ARGUMENT
    }

    private final Code code;

    public TenantRecommendedRoleException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code getCode() {
        return code;
    }
}
