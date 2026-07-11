package com.wensaas.core.impl.event;

import com.wensaas.core.api.event.DomainEventPublisher;
import com.wensaas.core.api.idempotent.IdempotencyGuard;
import com.wensaas.core.api.mq.MqConsumeSupport;
import com.wensaas.core.impl.event.subscribe.EventSubscriptionDispatcherConsumer;
import com.wensaas.core.impl.event.subscribe.EventSubscriptionHandlerValidator;
import com.wensaas.core.impl.spi.registry.EventSubscriptionRegistry;
import org.apache.rocketmq.client.core.RocketMQClientTemplate;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ListableBeanFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * 业务领域事件总线装配。
 *
 * <p>{@code wen-saas-boot.event.enabled=true}（默认）时装配 RocketMQ Publisher；
 * 关闭场景由 {@code wen-saas-boot.event.enabled=false} 控制（业务方需自行 noop 实现）。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(EventProperties.class)
@ConditionalOnProperty(prefix = "wen-saas-boot.event", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class EventConfiguration {

    private static final Logger log = LoggerFactory.getLogger(EventConfiguration.class);

    private final EventProperties props;

    public EventConfiguration(EventProperties props) {
        this.props = props;
    }

    @PostConstruct
    public void logReady() {
        log.info("[DomainEvent] enabled, topic={}, dispatcher-group={}",
                props.getTopic(), props.getDispatcherConsumerGroup());
    }

    @Bean
    @ConditionalOnMissingBean(DomainEventPublisher.class)
    public DomainEventPublisher domainEventPublisher(ObjectProvider<RocketMQClientTemplate> templateProvider,
                                                      EventProperties props) {
        return new RocketMqDomainEventPublisher(templateProvider, props);
    }

    @Bean
    public EventSubscriptionHandlerValidator eventSubscriptionHandlerValidator(
            EventSubscriptionRegistry registry, ListableBeanFactory beanFactory) {
        return new EventSubscriptionHandlerValidator(registry, beanFactory);
    }

    @Bean
    @ConditionalOnProperty(name = "wen-saas-boot.mq.consumers-enabled",
            havingValue = "true", matchIfMissing = true)
    public EventSubscriptionDispatcherConsumer eventSubscriptionDispatcherConsumer(
            EventSubscriptionRegistry registry, BeanFactory beanFactory, EventProperties props,
            IdempotencyGuard idempotency, MqConsumeSupport mqSupport) {
        return new EventSubscriptionDispatcherConsumer(registry, beanFactory, props, idempotency, mqSupport);
    }
}
