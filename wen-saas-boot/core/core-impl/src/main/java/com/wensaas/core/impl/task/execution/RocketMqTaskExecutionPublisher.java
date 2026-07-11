package com.wensaas.core.impl.task.execution;

import com.alibaba.fastjson2.JSON;
import org.apache.rocketmq.client.apis.producer.SendReceipt;
import org.apache.rocketmq.client.core.RocketMQClientTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import com.wensaas.core.impl.task.TaskProperties;

import java.util.concurrent.CompletableFuture;

/**
 * 默认 {@link TaskExecutionPublisher} 实现：走 RocketMQ 5.x 异步发送（{@code asyncSendNormalMessage}）。
 *
 * <p>失败仅 log.error（不本地降级落盘，与操作日志同惯例）。RocketMQClientTemplate 通过 {@link ObjectProvider}
 * 兜底，rocketmq autoconfig 未装配时（罕见，测试场景）抛 IllegalStateException 强烈提示 —— 任务中心强依赖 MQ。
 */
public class RocketMqTaskExecutionPublisher implements TaskExecutionPublisher {

    private static final Logger log = LoggerFactory.getLogger(RocketMqTaskExecutionPublisher.class);

    private final ObjectProvider<RocketMQClientTemplate> templateProvider;
    private final TaskProperties props;

    public RocketMqTaskExecutionPublisher(ObjectProvider<RocketMQClientTemplate> templateProvider, TaskProperties props) {
        this.templateProvider = templateProvider;
        this.props = props;
    }

    @Override
    public void publish(TaskExecutionMessage message) {
        RocketMQClientTemplate template = templateProvider.getIfAvailable();
        if (template == null) {
            throw new IllegalStateException("RocketMQClientTemplate 未装配；任务中心强依赖 MQ，"
                    + "请确认 rocketmq.producer.endpoints 已配置且 autoconfig 未被 exclude");
        }
        String json = JSON.toJSONString(message);
        try {
            CompletableFuture<SendReceipt> future = new CompletableFuture<>();
            future.whenComplete((receipt, ex) -> {
                if (ex != null) {
                    log.error("task.publish.fail taskId={} requestId={}", message.taskId(), message.requestId(), ex);
                } else if (log.isDebugEnabled()) {
                    log.debug("task.publish.ok taskId={} requestId={} msgId={}",
                            message.taskId(), message.requestId(), receipt.getMessageId());
                }
            });
            template.asyncSendNormalMessage(props.getExecuteTopic(), json, future);
        } catch (RuntimeException e) {
            log.error("task.publish.send.fail taskId={} requestId={} reason={}",
                    message.taskId(), message.requestId(), e.getMessage(), e);
        }
    }
}
