package com.wensaas.core.impl.webhook.dispatch;

import com.alibaba.fastjson2.JSON;
import com.wensaas.core.api.event.EventEnvelope;
import com.wensaas.core.api.request.RequestContext;
import com.wensaas.core.api.tenant.TenantContext;
import com.wensaas.core.impl.event.EventProperties;
import com.wensaas.core.api.mq.MqConsumeSupport;
import com.wensaas.core.impl.tenant.web.TenantActivityGuard;
import org.apache.rocketmq.client.annotation.RocketMQMessageListener;
import org.apache.rocketmq.client.apis.consumer.ConsumeResult;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.apache.rocketmq.client.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import com.wensaas.core.impl.webhook.SysWebhook;
import com.wensaas.core.impl.webhook.SysWebhookMapper;

/**
 * Webhook Dispatcher Consumer。
 *
 * <p>订阅 {@code wen_event} topic：解码 EventEnvelope → 恢复 Tenant/RequestContext →
 * 查 {@code sys_webhook WHERE tenant_id=? AND status='active' AND subscribed_events @> ?::jsonb}
 * → 为每个匹配 webhook 投递 {@link WebhookSendEnvelope}（attempt=1）到 {@code wen_webhook_send}。
 *
 * <p>注意：本类仅负责扇出，不做 HTTP 推送（推送由 {@link WebhookSenderConsumer} 处理；
 * 这样 Dispatcher 处理速度快，且 Sender 端可以独立调整并发度 / 重试退避）。
 */
@RocketMQMessageListener(
        endpoints = "${rocketmq.push-consumer.endpoints:}",
        sslEnabled = false,
        topic = "${wen-saas-boot.event.topic:wen_event}",
        consumerGroup = "${wen-saas-boot.event.dispatcher-consumer-group:wen-saas-boot-event-dispatcher}")
public class WebhookDispatcherConsumer implements RocketMQListener {

    private static final Logger log = LoggerFactory.getLogger(WebhookDispatcherConsumer.class);

    private final SysWebhookMapper webhookMapper;
    private final WebhookSendPublisher sendPublisher;
    private final TenantActivityGuard activityGuard;
    private final EventProperties eventProps;
    private final MqConsumeSupport mqSupport;

    public WebhookDispatcherConsumer(SysWebhookMapper webhookMapper, WebhookSendPublisher sendPublisher,
                                      TenantActivityGuard activityGuard, EventProperties eventProps,
                                      MqConsumeSupport mqSupport) {
        this.webhookMapper = webhookMapper;
        this.sendPublisher = sendPublisher;
        this.activityGuard = activityGuard;
        this.eventProps = eventProps;
        this.mqSupport = mqSupport;
    }

    @Override
    public ConsumeResult consume(MessageView messageView) {
        String json = MqConsumeSupport.bodyOf(messageView);
        try {
            EventEnvelope event = JSON.parseObject(json, EventEnvelope.class);
            handle(event);
            return ConsumeResult.SUCCESS;
        } catch (RuntimeException e) {
            log.error("webhook.dispatch.fail body={} reason={}", json, e.getMessage(), e);
            return mqSupport.onConsumeFailure(eventProps.getDispatcherConsumerGroup(), eventProps.getTopic(),
                    messageView, json, e);
        }
    }

    /** 暴露给单元 / 集成测试同步消费（绕过 broker）。 */
    public void handle(EventEnvelope event) {
        long tenantId = event.tenantId();
        TenantContext.set(tenantId);
        RequestContext.restoreFromMessage(event.requestId());
        try {
            if (!activityGuard.isActive(tenantId)) {
                log.info("webhook.dispatch.skip.tenant-inactive tenantId={} eventType={}",
                        tenantId, event.eventType());
                return;
            }
            List<SysWebhook> matches = webhookMapper.selectActiveByEvent(tenantId, event.eventType());
            if (matches.isEmpty()) {
                if (log.isDebugEnabled()) {
                    log.debug("webhook.dispatch.no-subscribers tenantId={} eventType={} eventId={}",
                            tenantId, event.eventType(), event.eventId());
                }
                return;
            }
            for (SysWebhook hook : matches) {
                WebhookSendEnvelope envelope = new WebhookSendEnvelope(
                        hook.getId(), tenantId, event.requestId(), 1, event);
                sendPublisher.publish(envelope);
            }
            log.info("webhook.dispatch.ok tenantId={} eventType={} eventId={} fanout={}",
                    tenantId, event.eventType(), event.eventId(), matches.size());
        } finally {
            TenantContext.clear();
            RequestContext.clear();
        }
    }
}
