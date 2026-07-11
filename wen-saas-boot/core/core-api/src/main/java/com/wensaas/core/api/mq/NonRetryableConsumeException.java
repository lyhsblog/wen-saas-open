package com.wensaas.core.api.mq;

/**
 * 标记异常：消费过程中显式声明「本条消息不可重试」（毒丸）。
 *
 * <p>消费者业务校验确定失败、重试无意义时抛出（如消息语义非法、目标实体已不存在且不会恢复）。
 * {@link MqConsumeSupport#isPoison} 命中后直接落 {@code sys_mq_dead_letter}（source=poison_fast_fail）
 * 并 ack，不浪费 broker 有界重试。
 *
 * <p>对照：瞬时失败（网络抖动、DB 短暂不可用、Redis 超时等）<b>不要</b>抛本异常，让其按普通
 * RuntimeException 抛出，由 {@link MqConsumeSupport} 交 broker 重投；重试达上限再落死信（retry_exhausted）。
 */
public class NonRetryableConsumeException extends RuntimeException {

    public NonRetryableConsumeException(String message) {
        super(message);
    }

    public NonRetryableConsumeException(String message, Throwable cause) {
        super(message, cause);
    }
}
