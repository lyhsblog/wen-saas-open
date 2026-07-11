package com.wensaas.core.impl.task.progress;

import java.util.Optional;

/**
 * 任务进度 / 取消标志存储抽象。生产实现走 Redis；测试可替换内存实现。
 */
public interface TaskProgressStore {

    /** 写进度（频次由调用方 throttle 控制）。 */
    void writeProgress(long taskId, TaskProgress progress);

    /** 读进度；未命中 {@link Optional#empty()}。 */
    Optional<TaskProgress> readProgress(long taskId);

    /** 写终态进度并保留 ttl（让客户端读到最终状态后过期）。 */
    void writeTerminalProgress(long taskId, TaskProgress progress);

    /** 删除进度 key。 */
    void deleteProgress(long taskId);

    /** 设置取消标志（带 TTL）。 */
    void markCancelled(long taskId);

    /** 当前任务是否已被取消。 */
    boolean isCancelled(long taskId);

    /** 清取消标志（任务终态时调用）。 */
    void clearCancelled(long taskId);

    /** 进度数据。 */
    record TaskProgress(int progress, Integer total, Integer processed, Integer success, Integer failed,
                        String lastMessage, long updatedAt) {
    }
}
