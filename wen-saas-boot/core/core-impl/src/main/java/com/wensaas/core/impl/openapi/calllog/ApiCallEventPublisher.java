package com.wensaas.core.impl.openapi.calllog;

/**
 * 异步发布 {@link ApiCallEvent} 到消息中间件，让 Consumer 端批量入库。
 *
 * <p>测试可用 {@code @Primary CapturingPublisher} 替换，避免依赖 broker。
 */
public interface ApiCallEventPublisher {

    void publish(ApiCallEvent event);
}
