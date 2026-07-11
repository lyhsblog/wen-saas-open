package com.wensaas.core.api.notification.template;

import com.wensaas.core.api.notification.send.Channel;
/**
 * 租户后台 · 通知模板偏好业务异常。
 *
 * <p>Web 层映射：{@code TenantNotificationTemplateRestExceptionAdvice}。
 */
public class TenantNotificationTemplateException extends RuntimeException {

    public enum Code {
        /** 模板编码不存在（即平台无该默认模板）。 */
        TEMPLATE_NOT_FOUND,
        /** sys.* 底座预置模板受保护：主管理员不可停用。 */
        SYS_TEMPLATE_PROTECTED,
        /** 渠道非法：不在 {@link Channel} 枚举内，或该模板默认文案不含此渠道。 */
        INVALID_CHANNEL
    }

    private final Code code;

    public TenantNotificationTemplateException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
