package com.wensaas.core.impl.webhook.dispatch;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.wensaas.core.api.event.EventEnvelope;
import com.wensaas.core.api.idempotent.IdempotencyGuard;
import com.wensaas.core.api.request.RequestContext;
import com.wensaas.core.api.tenant.TenantContext;
import com.wensaas.core.impl.file.CredentialCipher;
import com.wensaas.core.api.mq.MqConsumeSupport;
import com.wensaas.core.impl.tenant.web.TenantActivityGuard;
import org.apache.rocketmq.client.annotation.RocketMQMessageListener;
import org.apache.rocketmq.client.apis.consumer.ConsumeResult;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.apache.rocketmq.client.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import com.wensaas.core.impl.webhook.SysWebhook;
import com.wensaas.core.impl.webhook.SysWebhookLog;
import com.wensaas.core.impl.webhook.SysWebhookLogMapper;
import com.wensaas.core.impl.webhook.SysWebhookMapper;
import com.wensaas.core.impl.webhook.WebhookProperties;

/**
 * Webhook Sender Consumer。
 *
 * <p>订阅 {@code wen_webhook_send} topic：取 {@link WebhookSendEnvelope} → 查 {@code sys_webhook}
 * → 解密 signing_secret → HMAC-SHA256 签名 → HTTP POST → 写 {@code sys_webhook_log}。
 * 失败按 attempt 进入指数退避重试，达到 {@code retry-times} 后写终态 {@code abandoned}。
 *
 * <p>连续失败自动停用：事件重试穷尽（abandoned）经 {@link WebhookFailureTracker} 计数，
 * 达阈值置 disabled 并告警租户主管理员；成功一次即清零。
 *
 * <p><b>幂等去重（M1）</b>：消费入口经 {@link IdempotencyGuard#runOnce} 守卫，去重维度为
 * 「同一条推送消息」= {@code webhookId:eventId:attempt}。这只挡 MQ at-least-once 对<em>同一条</em>
 * 消息的重复投递（避免重复 HTTP POST / 重复写 log / 重复扇出 retry）；业务退避重试发出的是
 * {@code attempt+1} 的新消息、去重键不同，正常放行。HTTP 失败被内部 catch（不抛）→ 声明保留、不重做；
 * 仅当解密 / Mapper / 解析等真异常抛出时 {@code runOnce} 自动 {@code release}，允许 MQ 重投重做。
 */
@RocketMQMessageListener(
        endpoints = "${rocketmq.push-consumer.endpoints:}",
        sslEnabled = false,
        topic = "${wen-saas-boot.webhook.send-topic:wen_webhook_send}",
        consumerGroup = "${wen-saas-boot.webhook.sender-consumer-group:wen-saas-boot-webhook-sender}")
public class WebhookSenderConsumer implements RocketMQListener {

    private static final Logger log = LoggerFactory.getLogger(WebhookSenderConsumer.class);

    /** 去重域：webhook 推送。 */
    static final String DEDUP_SCOPE = "webhook-send";

    private final SysWebhookMapper webhookMapper;
    private final SysWebhookLogMapper logMapper;
    private final WebhookHttpClient httpClient;
    private final WebhookSignatureSigner signer;
    private final CredentialCipher cipher;
    private final WebhookSendPublisher publisher;
    private final WebhookProperties props;
    private final TenantActivityGuard activityGuard;
    private final IdempotencyGuard idempotency;
    private final MqConsumeSupport mqSupport;
    private final WebhookFailureTracker failureTracker;

    public WebhookSenderConsumer(SysWebhookMapper webhookMapper,
                                  SysWebhookLogMapper logMapper,
                                  WebhookHttpClient httpClient,
                                  WebhookSignatureSigner signer,
                                  CredentialCipher cipher,
                                  WebhookSendPublisher publisher,
                                  WebhookProperties props,
                                  TenantActivityGuard activityGuard,
                                  IdempotencyGuard idempotency,
                                  MqConsumeSupport mqSupport,
                                  WebhookFailureTracker failureTracker) {
        this.webhookMapper = webhookMapper;
        this.logMapper = logMapper;
        this.httpClient = httpClient;
        this.signer = signer;
        this.cipher = cipher;
        this.publisher = publisher;
        this.props = props;
        this.activityGuard = activityGuard;
        this.idempotency = idempotency;
        this.mqSupport = mqSupport;
        this.failureTracker = failureTracker;
    }

    @Override
    public ConsumeResult consume(MessageView messageView) {
        String json = MqConsumeSupport.bodyOf(messageView);
        try {
            WebhookSendEnvelope envelope = JSON.parseObject(json, WebhookSendEnvelope.class);
            handle(envelope);
            return ConsumeResult.SUCCESS;
        } catch (RuntimeException e) {
            log.error("webhook.sender.consume.fail body={} reason={}", json, e.getMessage(), e);
            // 毒丸（解析 / 非法参数 / NonRetryable）→ 落死信并 ack；瞬时 → 交 broker 重投 / 重试耗尽落库
            return mqSupport.onConsumeFailure(props.getSenderConsumerGroup(), props.getSendTopic(),
                    messageView, json, e);
        }
    }

    /**
     * 暴露给测试。幂等去重守卫入口：同一条消息（webhookId:eventId:attempt）只处理一次。
     */
    public void handle(WebhookSendEnvelope envelope) {
        String eventId = envelope.event().eventId();
        String dedupId = envelope.webhookId() + ":" + eventId + ":" + envelope.attempt();
        boolean executed = idempotency.runOnce(DEDUP_SCOPE, dedupId, props.getDedupTtl(),
                () -> doHandle(envelope));
        if (!executed) {
            log.info("webhook.sender.skip.duplicate webhookId={} eventId={} attempt={}",
                    envelope.webhookId(), eventId, envelope.attempt());
        }
    }

    private void doHandle(WebhookSendEnvelope envelope) {
        TenantContext.set(envelope.tenantId());
        RequestContext.restoreFromMessage(envelope.requestId());
        try {
            if (!activityGuard.isActive(envelope.tenantId())) {
                log.info("webhook.sender.skip.tenant-inactive tenantId={} webhookId={}",
                        envelope.tenantId(), envelope.webhookId());
                return;
            }
            SysWebhook hook = webhookMapper.selectById(envelope.webhookId());
            if (hook == null || !"active".equals(hook.getStatus())) {
                log.info("webhook.sender.skip webhookId={} reason=not-found-or-inactive",
                        envelope.webhookId());
                return;
            }
            EventEnvelope event = envelope.event();
            String body = JSON.toJSONString(event);
            long timestamp = Instant.now().getEpochSecond();
            String secret = cipher.decrypt(hook.getSigningSecretEncrypted());
            String signature = signer.sign(secret, event.eventType(), timestamp, body);
            Map<String, String> headers = new LinkedHashMap<>(4);
            headers.put(props.getSignatureHeader(), signature);
            headers.put(props.getTimestampHeader(), String.valueOf(timestamp));
            headers.put(props.getEventHeader(), event.eventType());
            headers.put(props.getEventIdHeader(), event.eventId());

            long startNanos = System.nanoTime();
            try {
                WebhookHttpClient.Response resp = httpClient.post(hook.getTargetUrl(), body, headers);
                long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;
                if (resp.is2xx()) {
                    writeLog(envelope, hook, body, resp.status(), resp.body(), durationMs, null, "success");
                    failureTracker.onSuccess(hook);
                    return;
                }
                // 非 2xx：进入重试 / 终态
                String err = "HTTP " + resp.status();
                if (envelope.attempt() >= props.getRetryTimes()) {
                    writeLog(envelope, hook, body, resp.status(), resp.body(), durationMs, err, "abandoned");
                    failureTracker.onAbandoned(hook);
                    return;
                }
                writeLog(envelope, hook, body, resp.status(), resp.body(), durationMs, err, "failed");
                scheduleRetry(envelope);
            } catch (WebhookHttpClient.WebhookHttpException ex) {
                long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;
                String err = ex.getCause() == null ? ex.getMessage() : ex.getCause().toString();
                if (envelope.attempt() >= props.getRetryTimes()) {
                    writeLog(envelope, hook, body, null, null, durationMs, err, "abandoned");
                    failureTracker.onAbandoned(hook);
                    return;
                }
                writeLog(envelope, hook, body, null, null, durationMs, err, "failed");
                scheduleRetry(envelope);
            }
        } finally {
            TenantContext.clear();
            RequestContext.clear();
        }
    }

    private void scheduleRetry(WebhookSendEnvelope envelope) {
        WebhookSendEnvelope next = envelope.withNextAttempt();
        long delayMillis = props.backoffFor(next.attempt() - 1).toMillis();
        publisher.republish(next, delayMillis);
    }

    private void writeLog(WebhookSendEnvelope envelope, SysWebhook hook, String requestBody,
                          Integer httpStatus, String responseBody, long durationMs,
                          String errorMessage, String finalStatus) {
        SysWebhookLog row = new SysWebhookLog();
        row.setId(IdWorker.getId());
        row.setTenantId(envelope.tenantId());
        row.setWebhookId(hook.getId());
        row.setEventType(envelope.event().eventType());
        row.setEventId(envelope.event().eventId());
        row.setRequestBody(truncate(requestBody, props.getBodyTruncateBytes()));
        row.setRequestId(envelope.requestId());
        row.setHttpStatus(httpStatus);
        row.setResponseBody(responseBody);
        row.setDurationMs((int) durationMs);
        row.setErrorMessage(truncate(errorMessage, props.getBodyTruncateBytes()));
        row.setRetryCount(envelope.attempt() - 1);
        row.setFinalStatus(finalStatus);
        row.setCreatedAt(LocalDateTime.now());
        logMapper.insert(row);
    }

    private static String truncate(String s, int max) {
        if (s == null || s.length() <= max) return s;
        return s.substring(0, max);
    }
}
