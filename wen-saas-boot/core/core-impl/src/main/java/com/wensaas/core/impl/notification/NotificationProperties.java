package com.wensaas.core.impl.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import com.wensaas.core.impl.notification.adapter.EmailChannelAdapter;

/**
 * 通知模块配置。
 *
 * <pre>
 * wen-saas-boot:
 *   notification:
 *     enabled: true
 *     station-topic: wen_notify_station
 *     email-topic: wen_notify_email
 *     sms-topic: wen_notify_sms
 *     im-topic: wen_notify_im
 *     station-consumer-group: wen-saas-boot-notify-station
 *     email-consumer-group: wen-saas-boot-notify-email
 *     sms-consumer-group: wen-saas-boot-notify-sms
 *     im-consumer-group: wen-saas-boot-notify-im
 *     template-cache-ttl: 1h
 *     retry-times: 3
 *     retry-backoff:
 *       - 1s
 *       - 5s
 *       - 30s
 *     email-noop: true   # dev/test 默认 true；prod 接 SMTP 时改 false
 * </pre>
 */
@ConfigurationProperties(prefix = "wen-saas-boot.notification")
@Getter
@Setter
public class NotificationProperties {

    private boolean enabled = true;
    // RocketMQ topic 命名约束：^[%|a-zA-Z0-9_-]+$，用下划线
    private String stationTopic = "wen_notify_station";
    private String emailTopic = "wen_notify_email";
    private String smsTopic = "wen_notify_sms";
    private String imTopic = "wen_notify_im";
    private String stationConsumerGroup = "wen-saas-boot-notify-station";
    private String emailConsumerGroup = "wen-saas-boot-notify-email";
    private String smsConsumerGroup = "wen-saas-boot-notify-sms";
    private String imConsumerGroup = "wen-saas-boot-notify-im";
    private Duration templateCacheTtl = Duration.ofHours(1);
    /** 单条任务最多重试次数（首次失败 + 重试 = retryTimes 次尝试上限）。 */
    private int retryTimes = 3;
    /** 重试退避序列；长度应 ≥ retryTimes-1。 */
    private Duration[] retryBackoff = new Duration[] {
            Duration.ofSeconds(1), Duration.ofSeconds(5), Duration.ofSeconds(30)
    };
    /** dev/test 邮件 stub；true 时 EmailChannelAdapter 仅打日志不真发。 */
    private boolean emailNoop = true;
    /**
     * 邮件发件人（SMTP From）。留空则不显式设置 From，走 spring.mail 默认。
     * 阿里云 DirectMail 等要求 From 必须等于已验证的发信地址，需配成 {@code 显示名 <发信地址>}，
     * 且其中地址与 {@code spring.mail.username} 一致，例如 {@code Wen-SaaS <no-reply@mail.example.com>}。
     */
    private String emailFrom;
    /**
     * 全局邮件当日发送量硬上限（OTP + 业务通知合计，全实例共享），&lt;=0 表示不限。
     * EmailChannelAdapter 发信前原子占位、真实发出量恰不超过该值，按服务器时区自然日重置。
     * 防真发场景被脚本刷爆 / 第三方（阿里云）账单失控；dev/test 建议配小值兜底，prod 按供应商额度配或留空。
     */
    private int emailDailyLimit = 0;
    /**
     * 邮件收件人黑名单（大小写不敏感，精确匹配完整地址）。命中的地址不真发、仅打日志并按发送成功
     * 处理（不占当日发送量配额）。用于测试环境屏蔽真人邮箱，避免联调/演示流量打扰真实收件人。
     */
    private List<String> emailBlacklist = List.of();
    /**
     * 幂等去重声明保留时长。去重维度为「同一条投递消息」（channel + msgTaskId + attempt），
     * 挡住 MQ at-least-once 对同一条消息的重复投递（避免重复外发邮件/短信/IM）；只需覆盖单次投递重投窗口。
     */
    private Duration dedupTtl = Duration.ofMinutes(10);

}
