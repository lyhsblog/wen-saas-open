package com.wensaas.core.impl.task.execution;

/**
 * 任务执行 MQ 消息体。
 *
 * <p>消费侧凭 taskId 加载完整 sys_task；tenantId / requestId 透传以恢复异步上下文。
 */
public record TaskExecutionMessage(Long taskId, Long tenantId, String requestId) {
}
