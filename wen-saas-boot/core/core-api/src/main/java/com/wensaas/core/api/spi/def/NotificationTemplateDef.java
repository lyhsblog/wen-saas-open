package com.wensaas.core.api.spi.def;

import com.wensaas.core.api.notification.send.Channel;
import com.wensaas.core.api.notification.send.Priority;
import com.wensaas.core.api.notification.template.TemplateContent;
import com.wensaas.core.api.notification.template.TemplateVariableDef;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 通知模板贡献项。业务模块声明默认模板，启动期落 {@code sys_notification_template} (tenant_id=0)。
 *
 * <p>v1 起 record 扩为 7 字段；老调用方仍可用 {@link #of(String, String)}。
 *
 * @param code             模板编码 {@code {module}.{template_name}}，全局唯一
 * @param name             显示名
 * @param description      描述（可空）
 * @param defaultChannels  默认通道列表（业务方未传 channels 时使用）
 * @param contents         各通道文案；key 必须覆盖 defaultChannels 的全部项
 * @param availableVars    可用变量声明（含示例值，便于主管理员预览）
 * @param priority         优先级（NORMAL / IMPORTANT）；默认 NORMAL
 */
public record NotificationTemplateDef(
        String code,
        String name,
        String description,
        List<Channel> defaultChannels,
        Map<Channel, TemplateContent> contents,
        List<TemplateVariableDef> availableVars,
        Priority priority) {

    public NotificationTemplateDef {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(name, "name");
        if (code.isBlank()) {
            throw new IllegalArgumentException("code 不可空");
        }
        if (name.isBlank()) {
            throw new IllegalArgumentException("name 不可空");
        }
        if (defaultChannels == null) {
            defaultChannels = List.of();
        }
        if (contents == null) {
            contents = Map.of();
        }
        if (availableVars == null) {
            availableVars = List.of();
        }
        if (priority == null) {
            priority = Priority.NORMAL;
        }
        // defaultChannels 必须在 contents 中均有文案，避免发送时空指针
        for (Channel ch : defaultChannels) {
            if (!contents.containsKey(ch)) {
                throw new IllegalArgumentException(
                        "模板 " + code + " defaultChannels 含 " + ch + " 但 contents 未提供该通道文案");
            }
        }
    }

    /** 简单模板工厂：仅 code + name，无内容；保留作为 SPI v1 兼容入口（v1 模板需要后续填充）。 */
    public static NotificationTemplateDef of(String code, String name) {
        return new NotificationTemplateDef(code, name, null, List.of(), Map.of(), List.of(), Priority.NORMAL);
    }
}
