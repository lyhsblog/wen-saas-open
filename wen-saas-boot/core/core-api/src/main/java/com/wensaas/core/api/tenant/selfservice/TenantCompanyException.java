package com.wensaas.core.api.tenant.selfservice;

/**
 * 租户自助·公司信息业务异常。
 */
public class TenantCompanyException extends RuntimeException {

    public enum Code {
        /** 租户不存在（异常态）。 */
        TENANT_NOT_FOUND,
        /** 子域名格式非法（小写字母或数字开头，小写字母数字 + 连字符，不以连字符结尾，5-16 位）。 */
        INVALID_ROUTE_SLOT,
        /** 子域名被其他租户占用（全局唯一）。 */
        ROUTE_SLOT_TAKEN,
        /** 子域名 30 天内已改过一次。 */
        ROUTE_SLOT_CHANGE_TOO_SOON,
        /** Logo 文件不存在或不属于当前租户。 */
        LOGO_FILE_NOT_FOUND,
        /** Logo 格式 / 大小不合法。 */
        INVALID_LOGO
    }

    private final Code code;

    public TenantCompanyException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
