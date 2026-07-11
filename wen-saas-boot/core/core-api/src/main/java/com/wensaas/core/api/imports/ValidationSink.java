package com.wensaas.core.api.imports;

/**
 * 校验错误回收口：业务方 resolver 把发现的行级错误写回底座。
 */
@FunctionalInterface
public interface ValidationSink {

    /**
     * 标记某行某字段校验失败。
     *
     * @param rowNo   行号（1-based）
     * @param field   字段 key；整行级错误传 {@code null}
     * @param code    错误类型
     * @param message 可读说明
     */
    void addError(int rowNo, String field, ImportErrorCode code, String message);
}
