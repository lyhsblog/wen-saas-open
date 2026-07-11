package com.wensaas.core.impl.openapi.calllog;

import com.alibaba.fastjson2.JSON;
import com.wensaas.core.api.request.RequestContext;
import com.wensaas.core.api.mq.MqConsumeSupport;
import com.wensaas.core.impl.tenant.web.TenantActivityGuard;
import org.apache.rocketmq.client.annotation.RocketMQMessageListener;
import org.apache.rocketmq.client.apis.consumer.ConsumeResult;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.apache.rocketmq.client.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.wensaas.core.impl.openapi.OpenApiProperties;

/**
 * 开放 API 调用日志消费者：消息体 → {@link ApiCallEvent} → {@link ApiCallBatchBuffer}。
 */
@RocketMQMessageListener(
        endpoints = "${rocketmq.push-consumer.endpoints:}",
        sslEnabled = false,
        topic = "${wen-saas-boot.openapi.call-log.topic:wen_api_call_log}",
        consumerGroup = "${wen-saas-boot.openapi.call-log.consumer-group:wen-saas-boot-api-call-log}")
public class ApiCallEventConsumer implements RocketMQListener {

    private static final Logger log = LoggerFactory.getLogger(ApiCallEventConsumer.class);

    private final ApiCallBatchBuffer buffer;
    private final TenantActivityGuard activityGuard;
    private final OpenApiProperties props;
    private final MqConsumeSupport mqSupport;

    public ApiCallEventConsumer(ApiCallBatchBuffer buffer, TenantActivityGuard activityGuard,
                                OpenApiProperties props, MqConsumeSupport mqSupport) {
        this.buffer = buffer;
        this.activityGuard = activityGuard;
        this.props = props;
        this.mqSupport = mqSupport;
    }

    @Override
    public ConsumeResult consume(MessageView messageView) {
        String json = MqConsumeSupport.bodyOf(messageView);
        try {
            ApiCallEvent event = JSON.parseObject(json, ApiCallEvent.class);
            RequestContext.restoreFromMessage(event.requestId());
            if (!activityGuard.isActive(event.tenantId())) {
                log.info("apicall.skip.tenant-inactive tenantId={}", event.tenantId());
                return ConsumeResult.SUCCESS;
            }
            buffer.offer(event);
            return ConsumeResult.SUCCESS;
        } catch (RuntimeException e) {
            log.error("apicall.consume.fail body={} reason={}", json, e.getMessage(), e);
            return mqSupport.onConsumeFailure(props.getCallLog().getConsumerGroup(),
                    props.getCallLog().getTopic(), messageView, json, e);
        } finally {
            RequestContext.clear();
        }
    }
}
