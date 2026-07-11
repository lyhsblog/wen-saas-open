package com.wensaas.core.impl.notification.delivery;

import com.wensaas.core.api.mq.MqConsumeSupport;
import org.apache.rocketmq.client.annotation.RocketMQMessageListener;
import org.apache.rocketmq.client.apis.consumer.ConsumeResult;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.apache.rocketmq.client.core.RocketMQListener;
import com.wensaas.core.impl.notification.NotificationProperties;

/** 站内信通道 Consumer。 */
@RocketMQMessageListener(
        endpoints = "${rocketmq.push-consumer.endpoints:}",
        sslEnabled = false,
        topic = "${wen-saas-boot.notification.station-topic:wen_notify_station}",
        consumerGroup = "${wen-saas-boot.notification.station-consumer-group:wen-saas-boot-notify-station}")
public class StationNotificationConsumer implements RocketMQListener {

    private final MessageDeliveryRunner runner;
    private final NotificationProperties props;
    private final MqConsumeSupport mqSupport;

    public StationNotificationConsumer(MessageDeliveryRunner runner, NotificationProperties props,
                                       MqConsumeSupport mqSupport) {
        this.runner = runner;
        this.props = props;
        this.mqSupport = mqSupport;
    }

    @Override
    public ConsumeResult consume(MessageView messageView) {
        // 投递失败已在 runner 内收敛为 task 终态（不抛）；逃逸到这里的只有解析等毒丸 → 落死信并 ack
        return mqSupport.consume(props.getStationConsumerGroup(), props.getStationTopic(), messageView,
                json -> runner.handle(json));
    }
}
