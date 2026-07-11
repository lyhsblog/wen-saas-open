package com.wensaas.core.impl.task.execution;

/**
 * 任务执行消息发布接口。
 *
 * <p>v1 默认实现走 RocketMQ asyncSend；测试可注入 in-memory 实现。
 */
public interface TaskExecutionPublisher {

    void publish(TaskExecutionMessage message);
}
