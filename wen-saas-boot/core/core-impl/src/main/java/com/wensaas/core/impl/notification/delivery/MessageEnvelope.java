package com.wensaas.core.impl.notification.delivery;

import com.wensaas.core.api.notification.send.Channel;
import com.wensaas.core.api.notification.send.MessageCategory;
import com.wensaas.core.api.notification.send.NotificationCategory;
import com.wensaas.core.api.notification.send.Priority;

import java.util.Map;
import java.util.Objects;

/**
 * RocketMQ 消息体：单条 {@code sys_message_task} 拆出来的可消费单元。
 *
 * <p>Consumer 收到后 → 反查 task row → 调对应 {@link com.wensaas.core.api.notification.send.ChannelAdapter}。
 *
 * @param msgTaskId   sys_message_task.id（库主键）
 * @param taskId      业务任务 ID
 * @param tenantId    租户 ID
 * @param channel     目标通道
 * @param recipientId 收件人 user_id
 * @param templateCode 模板编码
 * @param category    站内信分类
 * @param notificationCategory 通知类别（用户偏好维度，可空）；非空时投递参与偏好过滤
 * @param priority    优先级
 * @param title       渲染后标题
 * @param body        渲染后正文
 * @param link        跳转链接
 * @param relatedType 关联类型
 * @param relatedId   关联 ID
 * @param sourceModule 来源模块
 * @param variables   原始变量 map
 * @param requestId   关联请求 ID（异步上下文用）
 * @param attempt     当前尝试次数（首次=1；重试递增）
 */
public record MessageEnvelope(
        long msgTaskId,
        String taskId,
        long tenantId,
        Channel channel,
        long recipientId,
        String templateCode,
        MessageCategory category,
        NotificationCategory notificationCategory,
        Priority priority,
        String title,
        String body,
        String link,
        String relatedType,
        String relatedId,
        String sourceModule,
        Map<String, Object> variables,
        String requestId,
        int attempt) {

    public MessageEnvelope {
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(taskId, "taskId");
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
        if (attempt < 1) {
            attempt = 1;
        }
    }

    public MessageEnvelope withAttempt(int next) {
        return new MessageEnvelope(msgTaskId, taskId, tenantId, channel, recipientId, templateCode,
                category, notificationCategory, priority, title, body, link, relatedType, relatedId,
                sourceModule, variables, requestId, next);
    }
}
