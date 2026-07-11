package com.wensaas.core.impl.webhook.integration;

import com.alibaba.fastjson2.JSON;
import com.wensaas.core.impl.webhook.dispatch.WebhookSendEnvelope;
import com.wensaas.core.impl.webhook.dispatch.WebhookSendPublisher;
import com.wensaas.core.impl.webhook.dispatch.WebhookSenderConsumer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 测试用 {@link WebhookSendPublisher}：缓存所有 publish / republish 的 envelope，
 * 不真发到 RocketMQ；{@code flushAllTo(consumer)} 同步驱动 sender 链路。
 */
public class CapturingWebhookSendPublisher implements WebhookSendPublisher {

    private final List<WebhookSendEnvelope> buffer = new CopyOnWriteArrayList<>();

    @Override
    public void publish(WebhookSendEnvelope envelope) {
        buffer.add(envelope);
    }

    @Override
    public void republish(WebhookSendEnvelope envelope, long delayMillis) {
        buffer.add(envelope);
    }

    public void clear() {
        buffer.clear();
    }

    public List<WebhookSendEnvelope> snapshot() {
        return List.copyOf(buffer);
    }

    public int size() {
        return buffer.size();
    }

    /**
     * 同步把当前缓存的所有 envelope 交给 {@link WebhookSenderConsumer#handle} 处理；
     * 处理过程中 sender 可能再次 publish/republish 进 buffer，循环直到没有新 envelope。
     */
    public void flushAllTo(WebhookSenderConsumer consumer) {
        int safety = 0;
        while (!buffer.isEmpty()) {
            List<WebhookSendEnvelope> snapshot = new ArrayList<>(buffer);
            buffer.clear();
            for (WebhookSendEnvelope env : snapshot) {
                consumer.handle(env);
            }
            if (++safety > 50) {
                throw new IllegalStateException("flushAllTo: 超过 50 轮仍有未消费 envelope，可能死循环");
            }
        }
    }

    /** 测试调试用：把当前缓存序列化打印。 */
    public String dump() {
        return JSON.toJSONString(buffer);
    }
}
