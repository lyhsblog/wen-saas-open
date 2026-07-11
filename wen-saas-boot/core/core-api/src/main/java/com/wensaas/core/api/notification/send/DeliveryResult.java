package com.wensaas.core.api.notification.send;

import java.util.Objects;

/**
 * 通道适配器投递结果。
 *
 * @param status      投递结果状态（SENT / FAILED / SKIPPED）
 * @param errorReason 失败 / 跳过原因（成功时为空）
 */
public record DeliveryResult(MessageStatus status, String errorReason) {

    public DeliveryResult {
        Objects.requireNonNull(status, "status");
        if (status == MessageStatus.PENDING) {
            throw new IllegalArgumentException("DeliveryResult 不应是 PENDING");
        }
    }

    public static DeliveryResult sent() {
        return new DeliveryResult(MessageStatus.SENT, null);
    }

    public static DeliveryResult failed(String reason) {
        return new DeliveryResult(MessageStatus.FAILED, reason);
    }

    public static DeliveryResult skipped(String reason) {
        return new DeliveryResult(MessageStatus.SKIPPED, reason);
    }
}
