package com.wensaas.core.api.notification.template;

import com.wensaas.core.api.notification.send.Channel;
/**
 * 通知模板（运营平台默认模板）管理业务异常。
 *
 * <p>Web 层映射：{@code NotificationTemplateAdminRestExceptionAdvice}。
 */
public class NotificationTemplateAdminException extends RuntimeException {

    public enum Code {
        /** 模板编码不存在（PUT/DELETE/启停/详情请求时）。 */
        TEMPLATE_NOT_FOUND,
        /** 创建时编码已存在（同 tenant_id=0 下 template_code 唯一）。 */
        CODE_CONFLICT,
        /** sys.* 底座预置模板受保护：不可改 / 不可停用 / 不可删除。 */
        SYS_TEMPLATE_PROTECTED,
        /** 渠道名非法（不在 {@link Channel} 枚举内）。 */
        INVALID_CHANNEL,
        /** 启用渠道的文案为空（标题/正文按渠道要求不能全空）。 */
        EMPTY_CONTENT
    }

    private final Code code;

    public NotificationTemplateAdminException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
