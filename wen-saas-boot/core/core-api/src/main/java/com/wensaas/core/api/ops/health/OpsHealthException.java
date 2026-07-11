package com.wensaas.core.api.ops.health;

/**
 * 客户健康度业务异常。Web 层 {@code OpsHealthRestExceptionAdvice} 映射为 HTTP 状态。
 */
public class OpsHealthException extends RuntimeException {

    public enum Code {
        /** 租户不存在。 */
        TENANT_NOT_FOUND,
        /** 当前运营不在数据范围内（OWNED 看非自己归属的租户）。 */
        FORBIDDEN
    }

    private final Code code;

    public OpsHealthException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
