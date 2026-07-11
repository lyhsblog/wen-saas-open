package com.wensaas.core.impl.task.execution;

import com.alibaba.fastjson2.JSON;
import com.wensaas.core.api.idempotent.IdempotencyGuard;
import com.wensaas.core.api.request.RequestContext;
import com.wensaas.core.api.tenant.TenantContext;
import com.wensaas.core.api.mq.MqConsumeSupport;
import com.wensaas.core.impl.tenant.web.TenantActivityGuard;
import org.apache.rocketmq.client.annotation.RocketMQMessageListener;
import org.apache.rocketmq.client.apis.consumer.ConsumeResult;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.apache.rocketmq.client.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.wensaas.core.impl.task.TaskProperties;

/**
 * 任务执行消费者：拉到 {@link TaskExecutionMessage} → 恢复上下文 → 调 {@link TaskRunner#run(long)}。
 *
 * <p>消费异常经 {@link MqConsumeSupport} 分流：毒丸（解析 / 非法参数 / NonRetryable）落死信并 ack；
 * 瞬时异常未达 {@code max-delivery-attempts} 返回 {@code FAILURE} 交 broker 重投，达上限落死信并 ack。
 *
 * <p><b>幂等去重（M6）</b>：执行经 {@link IdempotencyGuard#runOnce} 守卫，去重键为 {@code taskId}。
 * 这挡住 MQ at-least-once 对同一条任务执行消息的重复投递（避免重复执行任务副作用）；执行失败抛出
 * 时 {@code runOnce} 自动 {@code release}，使 MQ 重投能重新执行（保留原有「失败重试」语义）。
 */
@RocketMQMessageListener(
        endpoints = "${rocketmq.push-consumer.endpoints:}",
        sslEnabled = false,
        topic = "${wen-saas-boot.task.execute-topic:wen_task_execute}",
        consumerGroup = "${wen-saas-boot.task.execute-consumer-group:wen-saas-boot-task-execute}")
public class TaskExecutionConsumer implements RocketMQListener {

    private static final Logger log = LoggerFactory.getLogger(TaskExecutionConsumer.class);

    /** 去重域：任务执行。 */
    static final String DEDUP_SCOPE = "task-exec";

    private final TaskRunner runner;
    private final TenantActivityGuard activityGuard;
    private final IdempotencyGuard idempotency;
    private final TaskProperties props;
    private final MqConsumeSupport mqSupport;

    public TaskExecutionConsumer(TaskRunner runner, TenantActivityGuard activityGuard,
                                 IdempotencyGuard idempotency, TaskProperties props,
                                 MqConsumeSupport mqSupport) {
        this.runner = runner;
        this.activityGuard = activityGuard;
        this.idempotency = idempotency;
        this.props = props;
        this.mqSupport = mqSupport;
    }

    @Override
    public ConsumeResult consume(MessageView messageView) {
        String json = MqConsumeSupport.bodyOf(messageView);
        TaskExecutionMessage msg;
        try {
            msg = JSON.parseObject(json, TaskExecutionMessage.class);
        } catch (RuntimeException e) {
            log.error("task.consume.parse.fail body={} reason={}", json, e.getMessage(), e);
            // 解析异常恒为毒丸 → 落死信并 ack
            return mqSupport.onConsumeFailure(props.getExecuteConsumerGroup(), props.getExecuteTopic(),
                    messageView, json, e);
        }
        try {
            boolean executed = idempotency.runOnce(DEDUP_SCOPE, String.valueOf(msg.taskId()),
                    props.getDedupTtl(), () -> execute(msg));
            if (!executed) {
                log.info("task.skip.duplicate taskId={}", msg.taskId());
            }
            return ConsumeResult.SUCCESS;
        } catch (RuntimeException e) {
            // execute() 已记 task.consume.run.fail；这里按毒丸 / 重试耗尽 / 待重投分流
            return mqSupport.onConsumeFailure(props.getExecuteConsumerGroup(), props.getExecuteTopic(),
                    messageView, json, e);
        }
    }

    private void execute(TaskExecutionMessage msg) {
        try {
            RequestContext.restoreFromMessage(msg.requestId());
            if (msg.tenantId() != null) {
                TenantContext.set(msg.tenantId());
            }
            if (!activityGuard.isActive(msg.tenantId())) {
                log.info("task.skip.tenant-inactive taskId={} tenantId={}", msg.taskId(), msg.tenantId());
                return;
            }
            runner.run(msg.taskId());
        } catch (RuntimeException e) {
            log.error("task.consume.run.fail taskId={} requestId={} reason={}",
                    msg.taskId(), msg.requestId(), e.getMessage(), e);
            throw e;
        } finally {
            TenantContext.clear();
            RequestContext.clear();
        }
    }
}
