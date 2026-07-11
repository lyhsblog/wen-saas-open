package com.wensaas.core.api.tenant;

/**
 * 租户活跃度校验异常：租户处于暂停（status=0）或已过期（status=2）状态。
 *
 * <p>Web 层映射：HTTP 403（{@code TenantActivityRestExceptionAdvice}）。
 * MQ 消费者捕获后 ACK + 静默跳过（不抛出给 RocketMQ，避免无意义重试）。
 */
public class TenantActivityException extends RuntimeException {

    public enum Code {
        /** sys_tenant.status = 0 */
        TENANT_SUSPENDED,
        /** sys_tenant.status = 2 */
        TENANT_EXPIRED
    }

    private final Code code;

    public TenantActivityException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code getCode() {
        return code;
    }
}
