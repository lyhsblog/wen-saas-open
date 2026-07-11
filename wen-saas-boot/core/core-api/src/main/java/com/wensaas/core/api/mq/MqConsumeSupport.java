package com.wensaas.core.api.mq;

import org.apache.rocketmq.client.apis.consumer.ConsumeResult;
import org.apache.rocketmq.client.apis.message.MessageView;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * 消费异常分流（业务模块可用的 MQ 消费可靠性入口）：v5 消费者统一经此把失败分成
 * 「毒丸」「重试耗尽」「待重投」三态，直接产出 {@link ConsumeResult}，死信落库内聚在消费链路
 * （无独立 DLQ 监控）。底座装配唯一实现为 Spring Bean，消费者构造注入即可。
 *
 * <ul>
 *   <li><b>毒丸</b>（{@link #isPoison}）：解析异常（fastjson2 {@code JSONException}）、非法参数
 *       （{@link IllegalArgumentException}）、显式 {@link NonRetryableConsumeException}。
 *       落 {@code sys_mq_dead_letter}（source=poison_fast_fail）并返回 {@link ConsumeResult#SUCCESS}（ack），不重投。</li>
 *   <li><b>重试耗尽</b>：瞬时异常且 {@link MessageView#getDeliveryAttempt()} 已达
 *       {@code wen-saas-boot.mq.dead-letter.max-delivery-attempts}。落库（source=retry_exhausted）并 ack——
 *       死信表是唯一真相源，不再交 broker 投 {@code %DLQ%}。</li>
 *   <li><b>待重投</b>：瞬时异常且未达上限。返回 {@link ConsumeResult#FAILURE} 交 broker 按订阅组重投。</li>
 * </ul>
 *
 * <p>「应用层退避」（HTTP / 通道失败内部 catch + republish）不经本支——本支只接管「真异常」那条路径。
 */
public interface MqConsumeSupport {

    /** 沿 cause 链判定是否毒丸（不可重试）；实现须兜底任意环（含构造出的双向环），避免死循环。 */
    boolean isPoison(Throwable t);

    /** 从 {@link MessageView} 取出 UTF-8 消息体。 */
    static String bodyOf(MessageView mv) {
        ByteBuffer bb = mv.getBody();
        byte[] arr = new byte[bb.remaining()];
        bb.get(arr);
        return new String(arr, StandardCharsets.UTF_8);
    }

    /**
     * 包裹助手（无自定义 catch 的瘦消费者用）：解出 body 跑 work，失败按 {@link #onConsumeFailure} 分流。
     */
    default ConsumeResult consume(String consumerGroup, String topic, MessageView mv, Consumer<String> work) {
        String body = bodyOf(mv);
        try {
            work.accept(body);
            return ConsumeResult.SUCCESS;
        } catch (RuntimeException e) {
            return onConsumeFailure(consumerGroup, topic, mv, body, e);
        }
    }

    /**
     * catch 块助手（已有 try/catch + 具名日志的消费者用）：毒丸 / 重试耗尽 → 落库并 ack；
     * 其余瞬时 → 交 broker 重投。
     */
    ConsumeResult onConsumeFailure(String consumerGroup, String topic, MessageView mv,
                                   String body, RuntimeException e);
}
