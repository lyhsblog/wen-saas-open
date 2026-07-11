package com.wensaas.core.api.ops;

/**
 * 运营「业务模块」管理业务异常（只读管理页）。
 *
 * <p>Web 层映射：{@code OpsBusinessModuleRestExceptionAdvice}。
 */
public class OpsBusinessModuleException extends RuntimeException {

    public enum Code {
        /** 业务模块编码不存在（详情请求时）。 */
        MODULE_NOT_FOUND
    }

    private final Code code;

    public OpsBusinessModuleException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
