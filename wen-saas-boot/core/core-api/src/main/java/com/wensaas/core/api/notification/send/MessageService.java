package com.wensaas.core.api.notification.send;

import java.util.Optional;

/**
 * 业务方发送通知的统一入口。
 *
 * <p>业务方同步调用 {@link #send}，底座立即返回 taskId 并把后续投递切到 RocketMQ 异步执行。
 * 业务方通过 {@link #status} 查询聚合状态。
 *
 * <p>关闭通知模块（{@code wen-saas-boot.notification.enabled=false}）时使用 noop 实现，
 * send 返回一个固定 noop taskId，status 返回 Optional.empty。
 */
public interface MessageService {

    /**
     * 解析模板、变量替换、展开收件人、按通道拆分任务、入 RocketMQ。
     *
     * @return 业务任务 ID（同一次 send 所有拆分行共享）
     */
    MessageTaskId send(MessageSendRequest request);

    /**
     * 查询任务聚合状态。
     *
     * @return 任务不存在时返回 empty
     */
    Optional<MessageTaskStatus> status(MessageTaskId taskId);
}
