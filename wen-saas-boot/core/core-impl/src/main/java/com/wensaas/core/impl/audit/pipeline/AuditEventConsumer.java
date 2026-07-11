package com.wensaas.core.impl.audit.pipeline;

import com.alibaba.fastjson2.JSON;
import com.wensaas.core.api.audit.AuditEvent;
import com.wensaas.core.api.request.RequestContext;
import com.wensaas.core.api.mq.MqConsumeSupport;
import com.wensaas.core.impl.tenant.web.TenantActivityGuard;
import org.apache.rocketmq.client.annotation.RocketMQMessageListener;
import org.apache.rocketmq.client.apis.consumer.ConsumeResult;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.apache.rocketmq.client.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.wensaas.core.impl.audit.AuditProperties;

/**
 * 操作日志消费者：消息体 → AuditEvent → 入批量缓冲。
 */
@RocketMQMessageListener(
        endpoints = "${rocketmq.push-consumer.endpoints:}",
        sslEnabled = false,
        topic = "${wen-saas-boot.audit.operation-topic:wen_audit_operation}",
        consumerGroup = "${wen-saas-boot.audit.operation-consumer-group:wen-saas-boot-audit-op}")
public class AuditEventConsumer implements RocketMQListener {

    private static final Logger log = LoggerFactory.getLogger(AuditEventConsumer.class);

    private final OperationAuditBatchBuffer buffer;
    private final TenantActivityGuard activityGuard;
    private final AuditProperties props;
    private final MqConsumeSupport mqSupport;

    public AuditEventConsumer(OperationAuditBatchBuffer buffer, TenantActivityGuard activityGuard,
                              AuditProperties props, MqConsumeSupport mqSupport) {
        this.buffer = buffer;
        this.activityGuard = activityGuard;
        this.props = props;
        this.mqSupport = mqSupport;
    }

    @Override
    public ConsumeResult consume(MessageView messageView) {
        String json = MqConsumeSupport.bodyOf(messageView);
        try {
            AuditEvent event = JSON.parseObject(json, AuditEvent.class);
            RequestContext.restoreFromMessage(event.requestId());
            if (!activityGuard.isActive(event.tenantId())) {
                log.info("audit.skip.tenant-inactive tenantId={}", event.tenantId());
                return ConsumeResult.SUCCESS;
            }
            buffer.offer(event);
            return ConsumeResult.SUCCESS;
        } catch (RuntimeException e) {
            log.error("audit.consume.fail body={} reason={}", json, e.getMessage(), e);
            return mqSupport.onConsumeFailure(props.getOperationConsumerGroup(), props.getOperationTopic(),
                    messageView, json, e);
        } finally {
            RequestContext.clear();
        }
    }
}
