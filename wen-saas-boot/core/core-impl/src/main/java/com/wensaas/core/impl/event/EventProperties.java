package com.wensaas.core.impl.event;

import org.springframework.boot.context.properties.ConfigurationProperties;
import lombok.Getter;
import lombok.Setter;

import java.time.Duration;

/**
 * 业务领域事件总线配置。
 *
 * <pre>
 * wen-saas-boot:
 *   event:
 *     enabled: true
 *     topic: wen_event
 *     dispatcher-consumer-group: wen-saas-boot-event-dispatcher
 * </pre>
 */
@ConfigurationProperties(prefix = "wen-saas-boot.event")
@Getter
@Setter
public class EventProperties {

    private boolean enabled = true;
    // RocketMQ topic 名约束 ^[%|a-zA-Z0-9_-]+$，禁用点号
    private String topic = "wen_event";
    private String dispatcherConsumerGroup = "wen-saas-boot-event-dispatcher";

    /** 进程内订阅派发消费组（eventSubscriptions() SPI 的消费侧，与 webhook dispatcher 各自全量收）。 */
    private String subscriptionConsumerGroup = "wen-saas-boot-event-subscription";

    /** 订阅派发幂等去重声明保留时长（去重维度 handlerBeanName:eventId）。 */
    private Duration subscriptionDedupTtl = Duration.ofMinutes(10);

}
