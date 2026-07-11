package com.wensaas.core.api.task;

/**
 * 任务状态机。
 *
 * <pre>
 * PENDING ──► RUNNING ──► COMPLETED / FAILED
 *                 │
 *                 │ 用户取消
 *                 ▼
 *            CANCELLING ──► CANCELLED
 * </pre>
 *
 * <p>任务中心不做通用审批（无审批中间态）：导出审批由数据合规模块的审批单把关，
 * 审批通过后才提交任务，任务本身始终从 PENDING 起步。
 */
public enum TaskStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLING,
    CANCELLED;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }

    public boolean isRunning() {
        return this == RUNNING || this == CANCELLING;
    }
}
