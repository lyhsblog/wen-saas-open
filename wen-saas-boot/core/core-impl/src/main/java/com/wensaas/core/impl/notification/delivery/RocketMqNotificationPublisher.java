package com.wensaas.core.impl.notification.delivery;

import com.alibaba.fastjson2.JSON;
import com.wensaas.core.api.notification.send.Channel;
import org.apache.rocketmq.client.apis.producer.SendReceipt;
import org.apache.rocketmq.client.core.RocketMQClientTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import com.wensaas.core.impl.notification.NotificationProperties;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * 默认 {@link NotificationPublisher}：走 RocketMQ 5.x。
 *
 * <p>按 envelope.channel 路由到 4 个 topic（station / email / sms / im）。首次发布走
 * {@code asyncSendNormalMessage} 减少业务方延迟；重试（republish）走 {@code syncSendDelayMessage} 投递延时消息。
 *
 * <p>失败仅 log.error（不本地降级落盘）；与 audit / task 同惯例。
 */
public class RocketMqNotificationPublisher implements NotificationPublisher {

    private static final Logger log = LoggerFactory.getLogger(RocketMqNotificationPublisher.class);

    private final ObjectProvider<RocketMQClientTemplate> templateProvider;
    private final NotificationProperties props;

    public RocketMqNotificationPublisher(ObjectProvider<RocketMQClientTemplate> templateProvider,
                                          NotificationProperties props) {
        this.templateProvider = templateProvider;
        this.props = props;
    }

    @Override
    public void publish(MessageEnvelope envelope) {
        RocketMQClientTemplate template = requireTemplate();
        String topic = topicOf(envelope.channel());
        String json = JSON.toJSONString(envelope);
        try {
            CompletableFuture<SendReceipt> future = new CompletableFuture<>();
            future.whenComplete((receipt, ex) -> {
                if (ex != null) {
                    log.error("notify.publish.fail msgTaskId={} channel={}",
                            envelope.msgTaskId(), envelope.channel(), ex);
                } else if (log.isDebugEnabled()) {
                    log.debug("notify.publish.ok msgTaskId={} channel={} msgId={}",
                            envelope.msgTaskId(), envelope.channel(), receipt.getMessageId());
                }
            });
            template.asyncSendNormalMessage(topic, json, future);
        } catch (RuntimeException e) {
            log.error("notify.publish.send.fail msgTaskId={} channel={} reason={}",
                    envelope.msgTaskId(), envelope.channel(), e.getMessage(), e);
        }
    }

    @Override
    public void republish(MessageEnvelope envelope, long delayMillis) {
        RocketMQClientTemplate template = requireTemplate();
        String topic = topicOf(envelope.channel());
        String json = JSON.toJSONString(envelope);
        try {
            SendReceipt r = template.syncSendDelayMessage(topic, json, Duration.ofMillis(delayMillis));
            if (log.isDebugEnabled()) {
                log.debug("notify.republish.ok msgTaskId={} channel={} attempt={} delayMs={} msgId={}",
                        envelope.msgTaskId(), envelope.channel(), envelope.attempt(), delayMillis, r.getMessageId());
            }
        } catch (RuntimeException e) {
            log.error("notify.republish.fail msgTaskId={} channel={} attempt={} delayMs={} reason={}",
                    envelope.msgTaskId(), envelope.channel(), envelope.attempt(), delayMillis, e.getMessage(), e);
        }
    }

    private RocketMQClientTemplate requireTemplate() {
        RocketMQClientTemplate t = templateProvider.getIfAvailable();
        if (t == null) {
            throw new IllegalStateException("RocketMQClientTemplate 未装配；通知模块强依赖 MQ，"
                    + "请确认 rocketmq.producer.endpoints 已配置且 autoconfig 未被 exclude");
        }
        return t;
    }

    private String topicOf(Channel channel) {
        if (channel == Channel.STATION) return props.getStationTopic();
        if (channel == Channel.EMAIL)   return props.getEmailTopic();
        if (channel == Channel.SMS)     return props.getSmsTopic();
        // WECHAT / WECOM / DINGTALK 共用 IM topic
        return props.getImTopic();
    }
}
