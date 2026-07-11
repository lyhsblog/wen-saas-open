package com.wensaas.core.api.event;

/**
 * 业务领域事件发布 SPI。默认实现 {@code RocketMqDomainEventPublisher} 走 RocketMQ topic
 * {@code wen_event}；下游 Webhook Dispatcher Consumer / 业务方 Consumer 按事件类型订阅。
 *
 * <p>发布失败默认仅 log.error 不本地降级（与 audit / task / notification 同惯例）；
 * 强一致场景由业务方自行做事务消息 / 出站表。
 */
public interface DomainEventPublisher {

    /**
     * 发布完整事件信封。{@code requestId} 通常由实现从 {@code RequestContext.get()} 自动补全。
     */
    void publish(EventEnvelope envelope);

    /**
     * 便捷重载：实现自动补全 {@code tenantId}（从 {@code TenantContext.get()} 取）/
     * {@code eventId}（雪花）/ {@code occurredAt}（now）/ {@code requestId}（从 {@code RequestContext.get()} 取）。
     */
    void publish(String eventType, Object payload);
}
