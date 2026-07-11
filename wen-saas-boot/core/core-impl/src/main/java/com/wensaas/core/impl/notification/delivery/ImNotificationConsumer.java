package com.wensaas.core.impl.notification.delivery;

import com.wensaas.core.api.mq.MqConsumeSupport;
import org.apache.rocketmq.client.annotation.RocketMQMessageListener;
import org.apache.rocketmq.client.apis.consumer.ConsumeResult;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.apache.rocketmq.client.core.RocketMQListener;
import com.wensaas.core.impl.notification.NotificationProperties;

/** IM 通道 Consumer：微信 / 企微 / 钉钉合用一个 topic（envelope.channel 区分 adapter）。 */
@RocketMQMessageListener(
        endpoints = "${rocketmq.push-consumer.endpoints:}",
        sslEnabled = false,
        topic = "${wen-saas-boot.notification.im-topic:wen_notify_im}",
        consumerGroup = "${wen-saas-boot.notification.im-consumer-group:wen-saas-boot-notify-im}")
public class ImNotificationConsumer implements RocketMQListener {

    private final MessageDeliveryRunner runner;
    private final NotificationProperties props;
    private final MqConsumeSupport mqSupport;

    public ImNotificationConsumer(MessageDeliveryRunner runner, NotificationProperties props,
                                  MqConsumeSupport mqSupport) {
        this.runner = runner;
        this.props = props;
        this.mqSupport = mqSupport;
    }

    @Override
    public ConsumeResult consume(MessageView messageView) {
        return mqSupport.consume(props.getImConsumerGroup(), props.getImTopic(), messageView,
                json -> runner.handle(json));
    }
}
