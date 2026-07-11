package com.wensaas.core.api.ops;

/**
 * 运营个人中心业务异常。
 */
public class OpsProfileException extends RuntimeException {

    public enum Code {
        USER_NOT_FOUND,
        OLD_PASSWORD_MISMATCH,
        WEAK_PASSWORD,
        SESSION_NOT_FOUND,
        CANNOT_REVOKE_CURRENT_SESSION,
        /** 头像：超出大小上限或非 JPG/PNG 格式。 */
        INVALID_AVATAR,
        /** 改手机号：账号未设置旧手机或旧邮箱（无法走双通道）。 */
        MOBILE_CHANGE_PREREQ_MISSING,
        /** 改手机号：未按顺序走完前序步骤。 */
        MOBILE_CHANGE_ILLEGAL_STATE,
        /** 改手机号：动态码错误。 */
        MOBILE_CHANGE_CODE_INVALID,
        /** 改手机号：新手机号格式错误或与旧号相同。 */
        MOBILE_CHANGE_INVALID_NEW
    }

    private final Code code;

    public OpsProfileException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
