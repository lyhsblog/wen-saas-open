package com.wensaas.core.api.notification.template;

import java.util.Map;
import java.util.Objects;
import com.wensaas.core.api.notification.send.Channel;
import com.wensaas.core.api.notification.send.ChannelAdapter;
import com.wensaas.core.api.notification.send.MessageCategory;
import com.wensaas.core.api.notification.send.MessageService;
import com.wensaas.core.api.notification.send.Priority;

/**
 * 模板渲染完成后的单条消息载荷（喂给 {@link ChannelAdapter}）。
 *
 * <p>由 MessageService 在解析模板 + 变量替换后构造；适配器仅消费已渲染数据，不感知模板。
 *
 * @param tenantId     租户 ID
 * @param taskId       业务任务 ID
 * @param channel      目标通道
 * @param templateCode 模板编码（仅供日志/审计）
 * @param category     站内信分类
 * @param priority     优先级
 * @param title        渲染后的标题（可空，短信场景）
 * @param body         渲染后的正文
 * @param link         点击跳转链接
 * @param relatedType  关联业务类型
 * @param relatedId    关联业务 ID
 * @param sourceModule 来源模块（templateCode 前缀；如 "crm"、"sys"）
 * @param variables    原始变量 map（用于通道特殊处理；通常无需读）
 */
public record RenderedMessage(
        long tenantId,
        String taskId,
        Channel channel,
        String templateCode,
        MessageCategory category,
        Priority priority,
        String title,
        String body,
        String link,
        String relatedType,
        String relatedId,
        String sourceModule,
        Map<String, Object> variables) {

    public RenderedMessage {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(body, "body");
        if (variables == null) {
            variables = Map.of();
        }
        if (category == null) {
            category = MessageCategory.BUSINESS;
        }
        if (priority == null) {
            priority = Priority.NORMAL;
        }
    }
}
