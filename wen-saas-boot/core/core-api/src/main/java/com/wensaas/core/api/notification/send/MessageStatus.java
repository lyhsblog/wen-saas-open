package com.wensaas.core.api.notification.send;

/**
 * 消息任务状态。落 {@code sys_message_task.status}。
 *
 * <p>状态机：{@link #PENDING} → ({@link #SENT} | {@link #FAILED} | {@link #SKIPPED})
 */
public enum MessageStatus {
    /** 待发送（写入 MQ 但尚未被 Consumer 处理或处理中）。 */
    PENDING,
    /** 已成功投递到目标通道。 */
    SENT,
    /** 重试达到上限仍失败。 */
    FAILED,
    /** 通道适配器声明不可触达（如收件人无邮箱），不计为失败。 */
    SKIPPED
}
