package com.wensaas.core.impl.notification.delivery;

/**
 * 通知消息发布抽象。
 *
 * <p>主链路按 envelope.channel 路由到 4 个 topic（station/email/sms/im）。
 * Consumer 重试时也通过本接口投递（按指数退避把回环消息再次入队）。
 */
public interface NotificationPublisher {

    /** 首次发布：按 channel 路由到对应 topic。 */
    void publish(MessageEnvelope envelope);

    /** 重试发布：按 channel 路由 + 携带 delayLevel（毫秒）。 */
    void republish(MessageEnvelope envelope, long delayMillis);
}
