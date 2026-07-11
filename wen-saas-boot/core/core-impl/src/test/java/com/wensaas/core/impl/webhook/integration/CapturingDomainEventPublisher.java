package com.wensaas.core.impl.webhook.integration;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.wensaas.core.api.event.DomainEventPublisher;
import com.wensaas.core.api.event.EventEnvelope;
import com.wensaas.core.api.request.RequestContext;
import com.wensaas.core.api.tenant.TenantContext;
import com.wensaas.core.impl.webhook.dispatch.WebhookDispatcherConsumer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 测试用 {@link DomainEventPublisher}：缓存所有 publish 的 envelope，不真发到 RocketMQ；
 * {@code flushAllTo(dispatcher)} 同步驱动 dispatcher 链路（按 envelope 内 tenantId 还原 TenantContext）。
 *
 * <p>{@link #publish(String, Object)} 重载与生产 {@code RocketMqDomainEventPublisher} 同语义：自动补全
 * tenantId / eventId / occurredAt / requestId（从 {@link TenantContext} / {@link RequestContext} 取）。
 */
public class CapturingDomainEventPublisher implements DomainEventPublisher {

    private final List<EventEnvelope> buffer = new CopyOnWriteArrayList<>();

    @Override
    public void publish(EventEnvelope envelope) {
        buffer.add(envelope);
    }

    @Override
    public void publish(String eventType, Object payload) {
        Long tenantId = TenantContext.get();
        EventEnvelope envelope = new EventEnvelope(
                tenantId == null ? 0L : tenantId,
                eventType,
                String.valueOf(IdWorker.getId()),
                Instant.now(),
                payload,
                RequestContext.get());
        buffer.add(envelope);
    }

    public void clear() {
        buffer.clear();
    }

    public int size() {
        return buffer.size();
    }

    public List<EventEnvelope> snapshot() {
        return List.copyOf(buffer);
    }

    /** 同步把所有缓存交给 dispatcher 处理（dispatcher 自己设置 TenantContext）。 */
    public void flushAllTo(WebhookDispatcherConsumer dispatcher) {
        List<EventEnvelope> snapshot = new ArrayList<>(buffer);
        buffer.clear();
        for (EventEnvelope e : snapshot) {
            dispatcher.handle(e);
        }
    }
}
