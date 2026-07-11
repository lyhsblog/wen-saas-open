package com.wensaas.core.impl.notification.delivery;

import com.wensaas.core.api.mq.MqConsumeSupport;
import org.apache.rocketmq.client.annotation.RocketMQMessageListener;
import org.apache.rocketmq.client.apis.consumer.ConsumeResult;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.apache.rocketmq.client.core.RocketMQListener;
import com.wensaas.core.impl.notification.NotificationProperties;

/** 邮件通道 Consumer。 */
@RocketMQMessageListener(
        endpoints = "${rocketmq.push-consumer.endpoints:}",
        sslEnabled = false,
        topic = "${wen-saas-boot.notification.email-topic:wen_notify_email}",
        consumerGroup = "${wen-saas-boot.notification.email-consumer-group:wen-saas-boot-notify-email}")
public class EmailNotificationConsumer implements RocketMQListener {

    private final MessageDeliveryRunner runner;
    private final NotificationProperties props;
    private final MqConsumeSupport mqSupport;

    public EmailNotificationConsumer(MessageDeliveryRunner runner, NotificationProperties props,
                                     MqConsumeSupport mqSupport) {
        this.runner = runner;
        this.props = props;
        this.mqSupport = mqSupport;
    }

    @Override
    public ConsumeResult consume(MessageView messageView) {
        return mqSupport.consume(props.getEmailConsumerGroup(), props.getEmailTopic(), messageView,
                json -> runner.handle(json));
    }
}
