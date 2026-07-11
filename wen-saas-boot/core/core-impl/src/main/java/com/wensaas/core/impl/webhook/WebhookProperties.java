package com.wensaas.core.impl.webhook;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Webhook 推送配置。
 *
 * <pre>
 * wen-saas-boot:
 *   webhook:
 *     enabled: true
 *     send-topic: wen_webhook_send
 *     sender-consumer-group: wen-saas-boot-webhook-sender
 *     http-connect-timeout: 3s
 *     http-read-timeout: 10s
 *     retry-times: 3
 *     retry-backoff: [5s, 30s, 5m]
 *     body-truncate-bytes: 4000
 *     signature-header: X-Wen-Signature
 *     timestamp-header: X-Wen-Timestamp
 *     event-header: X-Wen-Event
 * </pre>
 */
@ConfigurationProperties(prefix = "wen-saas-boot.webhook")
@Getter
@Setter
public class WebhookProperties {

    private boolean enabled = true;
    private String sendTopic = "wen_webhook_send";
    private String senderConsumerGroup = "wen-saas-boot-webhook-sender";
    private Duration httpConnectTimeout = Duration.ofSeconds(3);
    private Duration httpReadTimeout = Duration.ofSeconds(10);
    private int retryTimes = 3;
    private List<Duration> retryBackoff = new ArrayList<>(List.of(
            Duration.ofSeconds(5), Duration.ofSeconds(30), Duration.ofMinutes(5)));
    private int bodyTruncateBytes = 4000;
    private String signatureHeader = "X-Wen-Signature";
    private String timestampHeader = "X-Wen-Timestamp";
    private String eventHeader = "X-Wen-Event";
    private String eventIdHeader = "X-Wen-Event-Id";

    /**
     * 幂等去重声明保留时长。去重维度为「同一条推送消息」（webhookId + eventId + attempt），
     * 用于挡住 MQ at-least-once 对同一条消息的重复投递；只需覆盖单次投递的重投窗口（非整条退避链）。
     */
    private Duration dedupTtl = Duration.ofMinutes(10);

    /**
     * 连续 N 个事件投递彻底失败（abandoned）后自动把订阅置 disabled 并站内信告警租户主管理员；
     * 按「事件」计数（单事件的多次重试只算一次），成功一次即清零。0 = 关闭自动停用。
     */
    private int autoDisableThreshold = 10;

    /**
     * 是否允许回调地址指向私网/环回地址。默认 false（保存时校验并拒绝，防 SSRF）；
     * 本地联调需要打到 localhost 接收端时在 dev 配置置 true。
     */
    private boolean allowPrivateTargets = false;

    /**
     * 计算 attempt（1-based）应等待的退避时长，超出数组按最后一项延伸。
     */
    public Duration backoffFor(int attempt) {
        if (retryBackoff == null || retryBackoff.isEmpty()) {
            return Duration.ZERO;
        }
        int idx = Math.min(attempt - 1, retryBackoff.size() - 1);
        return retryBackoff.get(Math.max(0, idx));
    }
}
