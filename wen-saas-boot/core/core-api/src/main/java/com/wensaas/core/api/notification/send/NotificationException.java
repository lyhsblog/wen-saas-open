package com.wensaas.core.api.notification.send;

/**
 * 通知模块业务异常。HTTP 适配（400/404 等）由 web 层 ExceptionAdvice 完成。
 */
public class NotificationException extends RuntimeException {

    public enum Code {
        /** 模板未找到（tenant 和 platform 默认都没有，或被停用）。 */
        TEMPLATE_NOT_FOUND,
        /** 模板状态非 active。 */
        TEMPLATE_INACTIVE,
        /** 模板缺少指定通道的文案（contents 中无对应通道键）。 */
        TEMPLATE_CHANNEL_MISSING,
        /** 收件人解析为空（角色/部门下无成员等）。 */
        NO_RECIPIENT_RESOLVED,
        /** 业务模块未声明该模板（仅 SPI 校验路径用）。 */
        TEMPLATE_NOT_DECLARED,
        /** 通道配置非法（如显式禁用站内信但未指定其他通道）。 */
        INVALID_CHANNEL_CONFIG,
        /** 通道适配器不存在（启动期未装配对应 Channel 的 Bean）。 */
        CHANNEL_ADAPTER_NOT_FOUND
    }

    private final Code code;

    public NotificationException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public NotificationException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
