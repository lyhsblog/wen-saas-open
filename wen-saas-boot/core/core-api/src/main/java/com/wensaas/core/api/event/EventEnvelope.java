package com.wensaas.core.api.event;

import java.time.Instant;
import java.util.Objects;

/**
 * 业务领域事件信封。
 *
 * @param tenantId   产生事件的租户；运营场景填 {@code 0}
 * @param eventType  事件类型，约定 {@code "{module}.{noun}_{verb}"} 形式（如 {@code crm_demo.customer_created}）
 * @param eventId    事件唯一 ID（用于消费侧幂等关联）；不提供时由 publisher 用雪花 ID 生成
 * @param occurredAt 事件发生时刻；不提供时由 publisher 取 {@link Instant#now()}
 * @param payload    事件负载（任意 POJO / Map）；publisher 序列化为 JSON 写入消息体
 * @param requestId  发布时的请求链路 ID（从 {@code RequestContext} 取）；消费侧 restoreFromMessage 还原
 */
public record EventEnvelope(long tenantId, String eventType, String eventId,
                            Instant occurredAt, Object payload, String requestId) {

    public EventEnvelope {
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        if (eventType.isBlank()) {
            throw new IllegalArgumentException("eventType 不能为空");
        }
    }
}
