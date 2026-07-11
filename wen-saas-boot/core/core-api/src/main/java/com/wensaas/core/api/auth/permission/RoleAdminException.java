package com.wensaas.core.api.auth.permission;

/**
 * 角色管理业务异常。
 */
public class RoleAdminException extends RuntimeException {

    public enum Code {
        /** 角色不存在 或 跨租户访问。404 */
        ROLE_NOT_FOUND,
        /** 角色编码冲突。409 */
        ROLE_CODE_DUPLICATE,
        /** 角色名冲突。409 */
        ROLE_NAME_DUPLICATE,
        /** 角色下还有成员，不可删。409 */
        ROLE_HAS_MEMBERS,
        /** 主管理员角色完全只读（不可改名 / 不可改权限 / 不可改数据范围 / 不可删）。400 */
        ROLE_SYSTEM_OWNER_READONLY,
        /** 预置角色（管理员 / 普通员工）禁止删除（仅可改权限点 / 数据范围）。400 */
        ROLE_SYSTEM_READONLY_FIELDS,
        /** 数据范围非法。400 */
        INVALID_DATA_SCOPE,
        /** 用户不存在 或 跨租户。404 */
        USER_NOT_FOUND
    }

    private final Code code;

    public RoleAdminException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
