package com.wensaas.core.api.auth.permission;

/**
 * 成员管理领域异常。
 *
 * <p>由 {@code MemberAdminRestExceptionAdvice} 映射为标准错误响应。
 */
public class MemberAdminException extends RuntimeException {

    public enum Code {
        MEMBER_NOT_FOUND,
        USERNAME_CONFLICT,
        EMAIL_CONFLICT,
        MOBILE_CONFLICT,
        DEPT_NOT_FOUND,
        MAIN_DEPT_REQUIRED,
        LAST_DEPT_PROTECTED,
        OWNER_PROTECTED,
        CANNOT_DISABLE_SELF,
        INVALID_ARGUMENT
    }

    private final Code code;

    public MemberAdminException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code getCode() {
        return code;
    }
}
