package com.wensaas.core.impl.audit.pipeline;

import com.alibaba.fastjson2.JSON;
import com.wensaas.core.api.audit.LoginAuditEvent;
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

@RocketMQMessageListener(
        endpoints = "${rocketmq.push-consumer.endpoints:}",
        sslEnabled = false,
        topic = "${wen-saas-boot.audit.login-topic:wen_audit_login}",
        consumerGroup = "${wen-saas-boot.audit.login-consumer-group:wen-saas-boot-audit-login}")
public class LoginAuditEventConsumer implements RocketMQListener {

    private static final Logger log = LoggerFactory.getLogger(LoginAuditEventConsumer.class);

    private final LoginAuditBatchBuffer buffer;
    private final TenantActivityGuard activityGuard;
    private final AuditProperties props;
    private final MqConsumeSupport mqSupport;

    public LoginAuditEventConsumer(LoginAuditBatchBuffer buffer, TenantActivityGuard activityGuard,
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
            LoginAuditEvent event = JSON.parseObject(json, LoginAuditEvent.class);
            RequestContext.restoreFromMessage(event.requestId());
            if (!activityGuard.isActive(event.tenantId())) {
                log.info("audit.login.skip.tenant-inactive tenantId={}", event.tenantId());
                return ConsumeResult.SUCCESS;
            }
            buffer.offer(event);
            return ConsumeResult.SUCCESS;
        } catch (RuntimeException e) {
            log.error("audit.login.consume.fail body={} reason={}", json, e.getMessage(), e);
            return mqSupport.onConsumeFailure(props.getLoginConsumerGroup(), props.getLoginTopic(),
                    messageView, json, e);
        } finally {
            RequestContext.clear();
        }
    }
}
