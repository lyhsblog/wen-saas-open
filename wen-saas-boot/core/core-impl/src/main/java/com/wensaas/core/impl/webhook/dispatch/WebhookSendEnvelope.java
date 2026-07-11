package com.wensaas.core.impl.webhook.dispatch;

import com.wensaas.core.api.event.EventEnvelope;

/**
 * 从 {@link WebhookDispatcherConsumer} 扇出到 {@link WebhookSenderConsumer} 的消息载荷。
 *
 * @param webhookId 目标 {@code sys_webhook.id}
 * @param tenantId  事件租户；Sender 还原 TenantContext 用
 * @param requestId 链路 ID；Sender 还原 RequestContext 用
 * @param attempt   本次推送尝试次数（1-based，达 {@code retryTimes} 后写终态 abandoned）
 * @param event     原始事件信封
 */
public record WebhookSendEnvelope(long webhookId, long tenantId, String requestId,
                                   int attempt, EventEnvelope event) {

    public WebhookSendEnvelope withNextAttempt() {
        return new WebhookSendEnvelope(webhookId, tenantId, requestId, attempt + 1, event);
    }
}
