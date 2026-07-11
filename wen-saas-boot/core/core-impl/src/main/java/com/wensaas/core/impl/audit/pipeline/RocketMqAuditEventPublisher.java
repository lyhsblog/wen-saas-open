package com.wensaas.core.impl.audit.pipeline;

import com.alibaba.fastjson2.JSON;
import com.wensaas.core.api.audit.AuditEvent;
import com.wensaas.core.api.audit.LoginAuditEvent;
import org.apache.rocketmq.client.apis.producer.SendReceipt;
import org.apache.rocketmq.client.core.RocketMQClientTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.wensaas.core.impl.audit.AuditProperties;

import java.util.concurrent.CompletableFuture;

/**
 * 默认 {@link AuditEventPublisher} 实现：走 RocketMQ 5.x 异步发送（{@code asyncSendNormalMessage}）。
 *
 * <p>失败仅 log.error（不本地降级落盘）。Consumer 端达投递上限的死信由 {@code MqConsumeSupport}
 * 落 {@code sys_mq_dead_letter}。
 */
public class RocketMqAuditEventPublisher implements AuditEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RocketMqAuditEventPublisher.class);

    private final RocketMQClientTemplate template;
    private final AuditProperties props;

    public RocketMqAuditEventPublisher(RocketMQClientTemplate template, AuditProperties props) {
        this.template = template;
        this.props = props;
    }

    @Override
    public void publish(AuditEvent event) {
        send(props.getOperationTopic(), JSON.toJSONString(event), "operation", event.id(), event.requestId());
    }

    @Override
    public void publish(LoginAuditEvent event) {
        send(props.getLoginTopic(), JSON.toJSONString(event), "login", event.id(), event.requestId());
    }

    private void send(String topic, String json, String type, Long id, String requestId) {
        try {
            CompletableFuture<SendReceipt> future = new CompletableFuture<>();
            future.whenComplete((receipt, ex) -> {
                if (ex != null) {
                    log.error("audit.publish.fail type={} id={} requestId={}", type, id, requestId, ex);
                } else if (log.isDebugEnabled()) {
                    log.debug("audit.publish.ok type={} id={} requestId={} msgId={}",
                            type, id, requestId, receipt.getMessageId());
                }
            });
            template.asyncSendNormalMessage(topic, json, future);
        } catch (RuntimeException e) {
            log.error("audit.publish.send.fail type={} id={} requestId={} reason={}",
                    type, id, requestId, e.getMessage(), e);
        }
    }
}
