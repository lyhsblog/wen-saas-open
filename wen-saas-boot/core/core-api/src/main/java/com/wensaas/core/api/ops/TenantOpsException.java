package com.wensaas.core.api.ops;

/**
 * 运营租户管理业务异常。
 */
public class TenantOpsException extends RuntimeException {

    public enum Code {
        DUPLICATE_TENANT_CODE,
        DUPLICATE_TENANT_NO,
        DUPLICATE_ADMIN_USERNAME,
        PACKAGE_NOT_FOUND,
        PACKAGE_NOT_ACTIVE,
        PLAN_NOT_FOUND,
        PLAN_ALREADY_ACTIVE,
        PLAN_ALREADY_REVOKED,
        TENANT_NOT_FOUND,
        NO_MAIN_ADMIN,
        MEMBER_NOT_FOUND,
        INVALID_STATUS,
        MAIN_ADMIN_SAME_AS_CURRENT,
        OLD_ADMIN_HANDLING_INVALID,
        NEW_ADMIN_NAME_REQUIRED,
        CSM_NOT_FOUND,
        /** 运营数据范围拒绝：OPS_OWNED 操作非名下（csm_user_id != self）租户 → 403 */
        FORBIDDEN
    }

    private final Code code;

    public TenantOpsException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
