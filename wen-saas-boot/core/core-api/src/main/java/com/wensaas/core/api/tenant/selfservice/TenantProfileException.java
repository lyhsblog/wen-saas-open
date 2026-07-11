package com.wensaas.core.api.tenant.selfservice;

/**
 * 租户个人中心业务异常（与运营侧 {@code OpsProfileException} 平行）。
 */
public class TenantProfileException extends RuntimeException {

    public enum Code {
        USER_NOT_FOUND,
        OLD_PASSWORD_MISMATCH,
        WEAK_PASSWORD,
        SESSION_NOT_FOUND,
        CANNOT_REVOKE_CURRENT_SESSION,
        /** 头像格式 / 大小不合法。 */
        INVALID_AVATAR,
        /** 改手机号：账号未设置旧手机或旧邮箱（无法走双通道）。 */
        MOBILE_CHANGE_PREREQ_MISSING,
        /** 改手机号：未按顺序走完前序步骤。 */
        MOBILE_CHANGE_ILLEGAL_STATE,
        /** 改手机号：动态码错误。 */
        MOBILE_CHANGE_CODE_INVALID,
        /** 改手机号：新手机号格式错误或与旧号相同。 */
        MOBILE_CHANGE_INVALID_NEW,
        /** 改邮箱：未按顺序走完前序步骤（未先获取新邮箱验证码）。 */
        EMAIL_CHANGE_ILLEGAL_STATE,
        /** 改邮箱：动态码错误。 */
        EMAIL_CHANGE_CODE_INVALID,
        /** 改邮箱：新邮箱格式错误或与旧邮箱相同。 */
        EMAIL_CHANGE_INVALID_NEW,
        /** 改邮箱：新邮箱已被租户内其他成员占用。 */
        EMAIL_CONFLICT
    }

    private final Code code;

    public TenantProfileException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
