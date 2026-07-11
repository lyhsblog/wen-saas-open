package com.wensaas.core.api.ops.team;

/**
 * 运营团队成员 / 角色管理业务异常。Web 层映射：{@code OpsTeamAdminRestExceptionAdvice}。
 */
public class OpsTeamAdminException extends RuntimeException {

    public enum Code {
        MEMBER_NOT_FOUND,
        USERNAME_CONFLICT,
        EMAIL_CONFLICT,
        MOBILE_CONFLICT,
        CANNOT_DISABLE_SELF,
        CANNOT_OPERATE_SUPER_ADMIN,
        CANNOT_DELETE_PRESET_ROLE,
        CANNOT_RENAME_PRESET_ROLE_CODE,
        SUPER_ADMIN_PERMISSIONS_READONLY,
        ROLE_NOT_FOUND,
        ROLE_CODE_CONFLICT,
        ROLE_NAME_CONFLICT,
        ROLE_HAS_MEMBERS,
        INVALID_DATA_SCOPE,
        INVALID_PERMISSION_CODE,
        WEAK_PASSWORD
    }

    private final Code code;

    public OpsTeamAdminException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
