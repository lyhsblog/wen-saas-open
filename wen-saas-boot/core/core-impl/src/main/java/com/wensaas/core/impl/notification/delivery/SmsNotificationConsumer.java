package com.wensaas.core.impl.notification.delivery;

import com.wensaas.core.api.mq.MqConsumeSupport;
import org.apache.rocketmq.client.annotation.RocketMQMessageListener;
import org.apache.rocketmq.client.apis.consumer.ConsumeResult;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.apache.rocketmq.client.core.RocketMQListener;
import com.wensaas.core.impl.notification.NotificationProperties;

/** 短信通道 Consumer（v1 stub adapter）。 */
@RocketMQMessageListener(
        endpoints = "${rocketmq.push-consumer.endpoints:}",
        sslEnabled = false,
        topic = "${wen-saas-boot.notification.sms-topic:wen_notify_sms}",
        consumerGroup = "${wen-saas-boot.notification.sms-consumer-group:wen-saas-boot-notify-sms}")
public class SmsNotificationConsumer implements RocketMQListener {

    private final MessageDeliveryRunner runner;
    private final NotificationProperties props;
    private final MqConsumeSupport mqSupport;

    public SmsNotificationConsumer(MessageDeliveryRunner runner, NotificationProperties props,
                                   MqConsumeSupport mqSupport) {
        this.runner = runner;
        this.props = props;
        this.mqSupport = mqSupport;
    }

    @Override
    public ConsumeResult consume(MessageView messageView) {
        return mqSupport.consume(props.getSmsConsumerGroup(), props.getSmsTopic(), messageView,
                json -> runner.handle(json));
    }
}
