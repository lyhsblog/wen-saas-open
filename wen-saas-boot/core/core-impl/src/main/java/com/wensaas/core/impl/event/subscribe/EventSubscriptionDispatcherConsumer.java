package com.wensaas.core.impl.event.subscribe;

import com.alibaba.fastjson2.JSON;
import com.wensaas.core.api.event.DomainEventHandler;
import com.wensaas.core.api.event.EventEnvelope;
import com.wensaas.core.api.idempotent.IdempotencyGuard;
import com.wensaas.core.api.mq.MqConsumeSupport;
import com.wensaas.core.api.request.RequestContext;
import com.wensaas.core.api.tenant.TenantContext;
import com.wensaas.core.impl.event.EventProperties;
import com.wensaas.core.impl.spi.registry.EventSubscriptionRegistry;
import org.apache.rocketmq.client.annotation.RocketMQMessageListener;
import org.apache.rocketmq.client.apis.consumer.ConsumeResult;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.apache.rocketmq.client.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;

import java.util.List;

/**
 * 进程内订阅派发消费者：{@code eventSubscriptions()} SPI 的消费侧。
 *
 * <p>监听统一事件 topic（与 webhook dispatcher 不同消费组、各自全量收），按
 * {@code EventEnvelope.eventType} 精确匹配 {@link EventSubscriptionRegistry} 里的订阅，
 * 逐个调用 {@link DomainEventHandler} Bean。
 *
 * <p>执行语义（与 webhook sender 同款守卫模式）：每个 (handler, eventId) 经
 * {@link IdempotencyGuard#runOnce} 只执行一次；handler 内抛异常 → 守卫自动释放声明并把异常
 * 上抛 → 整条消息交 broker 重投重做（已成功的 handler 被守卫挡住不重复），重试耗尽落死信表。
 * handler 在事件租户的 {@code TenantContext} 内执行。
 */
@RocketMQMessageListener(
        endpoints = "${rocketmq.push-consumer.endpoints:}",
        sslEnabled = false,
        topic = "${wen-saas-boot.event.topic:wen_event}",
        consumerGroup = "${wen-saas-boot.event.subscription-consumer-group:wen-saas-boot-event-subscription}")
public class EventSubscriptionDispatcherConsumer implements RocketMQListener {

    private static final Logger log = LoggerFactory.getLogger(EventSubscriptionDispatcherConsumer.class);

    /** 去重域：进程内事件订阅。 */
    static final String DEDUP_SCOPE = "event-subscription";

    private final EventSubscriptionRegistry registry;
    private final BeanFactory beanFactory;
    private final EventProperties props;
    private final IdempotencyGuard idempotency;
    private final MqConsumeSupport mqSupport;

    public EventSubscriptionDispatcherConsumer(EventSubscriptionRegistry registry,
                                               BeanFactory beanFactory,
                                               EventProperties props,
                                               IdempotencyGuard idempotency,
                                               MqConsumeSupport mqSupport) {
        this.registry = registry;
        this.beanFactory = beanFactory;
        this.props = props;
        this.idempotency = idempotency;
        this.mqSupport = mqSupport;
    }

    @Override
    public ConsumeResult consume(MessageView messageView) {
        String json = MqConsumeSupport.bodyOf(messageView);
        try {
            EventEnvelope event = JSON.parseObject(json, EventEnvelope.class);
            dispatch(event);
            return ConsumeResult.SUCCESS;
        } catch (RuntimeException e) {
            log.error("event.subscription.consume.fail body={} reason={}", json, e.getMessage(), e);
            return mqSupport.onConsumeFailure(props.getSubscriptionConsumerGroup(), props.getTopic(),
                    messageView, json, e);
        }
    }

    /** 暴露给测试。 */
    public void dispatch(EventEnvelope event) {
        List<EventSubscriptionRegistry.Entry> subs = registry.findByTopic(event.eventType());
        if (subs.isEmpty()) {
            return;
        }
        TenantContext.set(event.tenantId());
        RequestContext.restoreFromMessage(event.requestId());
        try {
            for (EventSubscriptionRegistry.Entry sub : subs) {
                String beanName = sub.subscription().handlerBeanName();
                // 启动期已校验 bean 存在且类型正确（EventSubscriptionHandlerValidator）
                DomainEventHandler handler = beanFactory.getBean(beanName, DomainEventHandler.class);
                String dedupId = beanName + ":" + event.eventId();
                boolean executed = idempotency.runOnce(DEDUP_SCOPE, dedupId,
                        props.getSubscriptionDedupTtl(), () -> handler.onEvent(event));
                if (!executed) {
                    log.info("event.subscription.skip.duplicate handler={} eventId={}",
                            beanName, event.eventId());
                }
            }
        } finally {
            TenantContext.clear();
            RequestContext.clear();
        }
    }
}
