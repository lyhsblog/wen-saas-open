package com.wensaas.core.impl.webhook.dispatch;

import com.alibaba.fastjson2.JSON;
import org.apache.rocketmq.client.apis.producer.SendReceipt;
import org.apache.rocketmq.client.core.RocketMQClientTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import com.wensaas.core.impl.webhook.WebhookProperties;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * 默认 {@link WebhookSendPublisher}：走 RocketMQ 5.x。
 *
 * <p>{@code wen_webhook_send} 必须建成 <b>DELAY 类型</b> topic（v5 同一 topic 只接受单一消息类型，
 * 重试链靠延时消息实现）；因此首发也走 {@code asyncSendDelayMessage}，用最小延时
 * {@link #FIRST_SEND_DELAY} 兑换类型一致（v5 要求投递时间戳严格晚于当前时刻）。
 * 失败仅 log.error，与 audit / task / notification 同惯例（不本地降级落盘）。
 */
public class RocketMqWebhookSendPublisher implements WebhookSendPublisher {

    private static final Logger log = LoggerFactory.getLogger(RocketMqWebhookSendPublisher.class);

    /** 首发的最小延时：DELAY topic 不收即时消息，1s 对 webhook 场景无感。 */
    private static final Duration FIRST_SEND_DELAY = Duration.ofSeconds(1);

    private final ObjectProvider<RocketMQClientTemplate> templateProvider;
    private final WebhookProperties props;

    public RocketMqWebhookSendPublisher(ObjectProvider<RocketMQClientTemplate> templateProvider,
                                         WebhookProperties props) {
        this.templateProvider = templateProvider;
        this.props = props;
    }

    @Override
    public void publish(WebhookSendEnvelope envelope) {
        RocketMQClientTemplate template = requireTemplate();
        String json = JSON.toJSONString(envelope);
        try {
            CompletableFuture<SendReceipt> future = new CompletableFuture<>();
            future.whenComplete((receipt, ex) -> {
                if (ex != null) {
                    log.error("webhook.publish.fail webhookId={} eventId={}",
                            envelope.webhookId(), envelope.event().eventId(), ex);
                } else if (log.isDebugEnabled()) {
                    log.debug("webhook.publish.ok webhookId={} eventId={} msgId={}",
                            envelope.webhookId(), envelope.event().eventId(), receipt.getMessageId());
                }
            });
            template.asyncSendDelayMessage(props.getSendTopic(), json, FIRST_SEND_DELAY, future);
        } catch (RuntimeException e) {
            log.error("webhook.publish.send.fail webhookId={} eventId={} attempt={} reason={}",
                    envelope.webhookId(), envelope.event().eventId(), envelope.attempt(),
                    e.getMessage(), e);
        }
    }

    @Override
    public void republish(WebhookSendEnvelope envelope, long delayMillis) {
        RocketMQClientTemplate template = requireTemplate();
        String json = JSON.toJSONString(envelope);
        try {
            SendReceipt r = template.syncSendDelayMessage(props.getSendTopic(), json, Duration.ofMillis(delayMillis));
            if (log.isDebugEnabled()) {
                log.debug("webhook.republish.ok webhookId={} attempt={} delayMs={} msgId={}",
                        envelope.webhookId(), envelope.attempt(), delayMillis, r.getMessageId());
            }
        } catch (RuntimeException e) {
            log.error("webhook.republish.fail webhookId={} attempt={} delayMs={} reason={}",
                    envelope.webhookId(), envelope.attempt(), delayMillis, e.getMessage(), e);
        }
    }

    private RocketMQClientTemplate requireTemplate() {
        RocketMQClientTemplate t = templateProvider.getIfAvailable();
        if (t == null) {
            throw new IllegalStateException("RocketMQClientTemplate 未装配；webhook 模块强依赖 MQ");
        }
        return t;
    }
}
