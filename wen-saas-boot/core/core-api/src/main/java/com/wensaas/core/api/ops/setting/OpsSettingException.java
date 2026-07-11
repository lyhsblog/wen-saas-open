package com.wensaas.core.api.ops.setting;

/**
 * 系统参数管理异常。Web 层映射：{@code OpsSettingRestExceptionAdvice}。
 */
public class OpsSettingException extends RuntimeException {

    public enum Code {
        SETTING_NOT_FOUND,
        INVALID_VALUE,
        UNKNOWN_CATEGORY,
        STORAGE_TEST_FAILED
    }

    private final Code code;

    public OpsSettingException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
