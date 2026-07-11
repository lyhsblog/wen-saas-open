package com.wensaas.core.impl.mq;

import org.apache.rocketmq.client.apis.message.MessageView;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 死信落库入参（内部值对象）。{@code tenant_id} 由 {@link DeadLetterRecorder} 从 {@code body} 尽力解析，
 * 故本载体不带 tenantId。
 *
 * @param consumerGroup    源消费组
 * @param originalTopic    原 topic
 * @param msgId            broker msgId（毒丸路径为 null）
 * @param msgKeys          业务 keys
 * @param body             消息体（落库前由 recorder 截断）
 * @param reconsumeTimes   已投递次数（毒丸路径为 null）
 * @param bornAt           消息产生时间
 * @param exceptionSummary 异常摘要
 * @param source           {@code retry_exhausted} | {@code poison_fast_fail}
 */
public record DeadLetterRecord(
        String consumerGroup,
        String originalTopic,
        String msgId,
        String msgKeys,
        String body,
        Integer reconsumeTimes,
        LocalDateTime bornAt,
        String exceptionSummary,
        String source) {

    public static final String SOURCE_RETRY_EXHAUSTED = "retry_exhausted";
    public static final String SOURCE_POISON = "poison_fast_fail";

    private static final int SUMMARY_MAX = 2000;

    /** 毒丸快失：消费侧识别不可重试异常后落库（无 broker msgId / 投递次数）。 */
    public static DeadLetterRecord poison(String consumerGroup, String topic, String body, Throwable e) {
        return new DeadLetterRecord(consumerGroup, topic, null, null, body, null,
                LocalDateTime.now(), summarize(e), SOURCE_POISON);
    }

    /** 瞬时异常重试耗尽：投递次数达上限仍失败，落库并 ack（不再交 broker 投 %DLQ%）。 */
    public static DeadLetterRecord retryExhausted(String consumerGroup, String topic,
                                                  MessageView mv, String body, Throwable e) {
        LocalDateTime bornAt = mv.getBornTimestamp() > 0
                ? LocalDateTime.ofInstant(Instant.ofEpochMilli(mv.getBornTimestamp()), ZoneId.systemDefault())
                : null;
        String keys = mv.getKeys().isEmpty() ? null : String.join(",", mv.getKeys());
        return new DeadLetterRecord(consumerGroup, topic, mv.getMessageId().toString(), keys, body,
                mv.getDeliveryAttempt(), bornAt, summarize(e), SOURCE_RETRY_EXHAUSTED);
    }

    private static String summarize(Throwable e) {
        if (e == null) return null;
        String s = e.getClass().getName() + ": " + e.getMessage();
        return s.length() > SUMMARY_MAX ? s.substring(0, SUMMARY_MAX) : s;
    }
}
