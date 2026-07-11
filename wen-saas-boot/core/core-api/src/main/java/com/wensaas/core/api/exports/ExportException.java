package com.wensaas.core.api.exports;

/**
 * 业务数据导出异常。
 */
public class ExportException extends RuntimeException {

    public enum Code {
        /** 导出对象未注册（无对应 ExportDataProvider）。 */
        OBJECT_UNKNOWN,
        /** 申请人缺少该对象的发起权限。 */
        FORBIDDEN,
        /** 需审批的导出未填申请理由。 */
        REASON_REQUIRED
    }

    private final Code code;

    public ExportException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
