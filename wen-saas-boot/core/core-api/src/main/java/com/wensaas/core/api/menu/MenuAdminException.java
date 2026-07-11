package com.wensaas.core.api.menu;

/**
 * 菜单管理业务异常。Web 层映射：{@code MenuAdminRestExceptionAdvice}。
 */
public class MenuAdminException extends RuntimeException {

    public enum Code {
        MENU_NOT_FOUND,
        NODE_KEY_CONFLICT,
        UNKNOWN_PERMISSION_CODE,
        INVALID_NODE_SHAPE,
        INVALID_PARENT,
        CANNOT_DELETE_SEED,
        MENU_HAS_CHILDREN
    }

    private final Code code;

    public MenuAdminException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
