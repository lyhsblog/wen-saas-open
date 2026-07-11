package com.wensaas.core.api.auth.permission;

import com.wensaas.core.api.auth.user.UserContext;
/**
 * 权限检查未通过。
 *
 * <p>由 {@code RequirePermissionAspect} 抛出，最终由 {@code AuthRestExceptionAdvice} 映射为
 * 401（{@link Code#NOT_AUTHENTICATED}）或 403（{@link Code#FORBIDDEN}）ProblemDetail。
 */
public class PermissionDeniedException extends RuntimeException {

    public enum Code {
        /** 未认证或会话失效（注解检查时 UserContext 为空）。 */
        NOT_AUTHENTICATED,
        /** 已认证但权限点不命中。 */
        FORBIDDEN
    }

    private final Code code;

    public PermissionDeniedException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code getCode() {
        return code;
    }
}
