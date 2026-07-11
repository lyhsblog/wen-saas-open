package com.wensaas.core.impl.webhook.dispatch;

/**
 * 投递 {@link WebhookSendEnvelope} 到 RocketMQ {@code wen_webhook_send}。
 *
 * <p>{@link #publish} 首次扇出走 {@code asyncSendNormalMessage}；{@link #republish} 走
 * {@code syncSendDelayMessage(topic, payload, Duration)} 投递延时消息做退避重试
 * （与 notification retry 模式同惯例）。
 */
public interface WebhookSendPublisher {

    void publish(WebhookSendEnvelope envelope);

    void republish(WebhookSendEnvelope envelope, long delayMillis);
}
