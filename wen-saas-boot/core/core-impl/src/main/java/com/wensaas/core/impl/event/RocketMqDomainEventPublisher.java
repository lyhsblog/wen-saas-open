package com.wensaas.core.impl.event;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.wensaas.core.api.event.DomainEventPublisher;
import com.wensaas.core.api.event.EventEnvelope;
import com.wensaas.core.api.request.RequestContext;
import com.wensaas.core.api.tenant.TenantContext;
import org.apache.rocketmq.client.apis.producer.SendReceipt;
import org.apache.rocketmq.client.core.RocketMQClientTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

/**
 * 默认 {@link DomainEventPublisher}：走 RocketMQ 5.x 异步发送（{@code asyncSendNormalMessage}）。
 *
 * <p>失败仅 log.error 不本地降级（与 audit / task / notification 同惯例）。下游
 * {@code WebhookDispatcherConsumer} 与业务方自定义 Consumer 通过 {@code @RocketMQMessageListener}
 * 监听 {@code wen_event} topic 各取所需。
 */
public class RocketMqDomainEventPublisher implements DomainEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RocketMqDomainEventPublisher.class);

    private final ObjectProvider<RocketMQClientTemplate> templateProvider;
    private final EventProperties props;

    public RocketMqDomainEventPublisher(ObjectProvider<RocketMQClientTemplate> templateProvider,
                                         EventProperties props) {
        this.templateProvider = templateProvider;
        this.props = props;
    }

    @Override
    public void publish(EventEnvelope envelope) {
        RocketMQClientTemplate template = requireTemplate();
        String json = JSON.toJSONString(envelope);
        try {
            CompletableFuture<SendReceipt> future = new CompletableFuture<>();
            future.whenComplete((receipt, ex) -> {
                if (ex != null) {
                    log.error("event.publish.fail eventType={} eventId={}",
                            envelope.eventType(), envelope.eventId(), ex);
                } else if (log.isDebugEnabled()) {
                    log.debug("event.publish.ok eventType={} eventId={} msgId={}",
                            envelope.eventType(), envelope.eventId(), receipt.getMessageId());
                }
            });
            template.asyncSendNormalMessage(props.getTopic(), json, future);
        } catch (RuntimeException e) {
            log.error("event.publish.send.fail eventType={} eventId={} reason={}",
                    envelope.eventType(), envelope.eventId(), e.getMessage(), e);
        }
    }

    @Override
    public void publish(String eventType, Object payload) {
        Long tenantId = TenantContext.get();
        String eventId = String.valueOf(IdWorker.getId());
        String requestId = RequestContext.get();
        EventEnvelope envelope = new EventEnvelope(
                tenantId == null ? 0L : tenantId,
                eventType,
                eventId,
                Instant.now(),
                payload,
                requestId);
        publish(envelope);
    }

    private RocketMQClientTemplate requireTemplate() {
        RocketMQClientTemplate t = templateProvider.getIfAvailable();
        if (t == null) {
            throw new IllegalStateException("RocketMQClientTemplate 未装配；事件总线强依赖 MQ，"
                    + "请确认 rocketmq.producer.endpoints 已配置且 autoconfig 未被 exclude");
        }
        return t;
    }
}
