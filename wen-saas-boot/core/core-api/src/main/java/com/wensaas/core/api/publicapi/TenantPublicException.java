package com.wensaas.core.api.publicapi;

/**
 * 公开租户信息查询业务异常。
 */
public class TenantPublicException extends RuntimeException {

    public enum Code {
        /** 租户编码不存在或已软删。404 */
        TENANT_NOT_FOUND
    }

    private final Code code;

    public TenantPublicException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
