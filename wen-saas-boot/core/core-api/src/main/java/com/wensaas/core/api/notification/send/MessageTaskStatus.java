package com.wensaas.core.api.notification.send;

import java.util.List;
import java.util.Objects;

/**
 * 业务方查 taskId 拿到的聚合状态。
 *
 * @param taskId   业务任务 ID
 * @param total    总条目（收件人 × 通道）
 * @param pending  待发
 * @param sent     已发
 * @param failed   失败
 * @param skipped  跳过
 * @param entries  各条目快照（按 created_at 排序）
 */
public record MessageTaskStatus(
        String taskId,
        int total,
        int pending,
        int sent,
        int failed,
        int skipped,
        List<Entry> entries) {

    public MessageTaskStatus {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(entries, "entries");
    }

    /**
     * 单条目快照。
     *
     * @param recipientId 收件人 user_id
     * @param channel     通道
     * @param status      当前状态
     * @param retryCount  已重试次数
     * @param errorReason 失败原因（仅 FAILED 时非空）
     */
    public record Entry(long recipientId,
                         Channel channel,
                         MessageStatus status,
                         int retryCount,
                         String errorReason) { }
}
