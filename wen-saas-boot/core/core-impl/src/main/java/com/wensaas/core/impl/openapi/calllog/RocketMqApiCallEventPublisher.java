package com.wensaas.core.impl.openapi.calllog;

import com.alibaba.fastjson2.JSON;
import org.apache.rocketmq.client.apis.producer.SendReceipt;
import org.apache.rocketmq.client.core.RocketMQClientTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import com.wensaas.core.impl.openapi.OpenApiProperties;

import java.util.concurrent.CompletableFuture;

/**
 * 默认 {@link ApiCallEventPublisher}：走 RocketMQ 5.x 异步发送（topic = {@code wen_api_call_log}）。
 * 与 audit / task / notification 同惯例：失败仅 log.error，不本地降级落盘。
 */
public class RocketMqApiCallEventPublisher implements ApiCallEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RocketMqApiCallEventPublisher.class);

    private final ObjectProvider<RocketMQClientTemplate> templateProvider;
    private final OpenApiProperties props;

    public RocketMqApiCallEventPublisher(ObjectProvider<RocketMQClientTemplate> templateProvider,
                                          OpenApiProperties props) {
        this.templateProvider = templateProvider;
        this.props = props;
    }

    @Override
    public void publish(ApiCallEvent event) {
        RocketMQClientTemplate template = templateProvider.getIfAvailable();
        if (template == null) {
            log.warn("apicall.publish.skip reason=template-missing event.id={}", event.id());
            return;
        }
        String json = JSON.toJSONString(event);
        try {
            CompletableFuture<SendReceipt> future = new CompletableFuture<>();
            future.whenComplete((receipt, ex) -> {
                if (ex != null) {
                    log.error("apicall.publish.fail id={} requestId={}", event.id(), event.requestId(), ex);
                } else if (log.isDebugEnabled()) {
                    log.debug("apicall.publish.ok id={} requestId={} msgId={}",
                            event.id(), event.requestId(), receipt.getMessageId());
                }
            });
            template.asyncSendNormalMessage(props.getCallLog().getTopic(), json, future);
        } catch (RuntimeException e) {
            log.error("apicall.publish.send.fail id={} requestId={} reason={}",
                    event.id(), event.requestId(), e.getMessage(), e);
        }
    }
}
