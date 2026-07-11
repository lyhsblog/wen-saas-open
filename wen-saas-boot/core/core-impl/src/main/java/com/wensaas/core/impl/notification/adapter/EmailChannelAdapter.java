package com.wensaas.core.impl.notification.adapter;

import com.wensaas.core.api.notification.send.Channel;
import com.wensaas.core.api.notification.send.ChannelAdapter;
import com.wensaas.core.api.notification.send.DeliveryResult;
import com.wensaas.core.api.notification.send.Recipient;
import com.wensaas.core.api.notification.template.RenderedMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 邮件通道：v1 走 {@link JavaMailSender}。
 *
 * <p>当 {@code wen-saas-boot.notification.email-noop=true}（dev/test 默认）时不真发邮件，
 * 仅 INFO 日志；prod 配 {@code spring.mail.*} 后改 false。
 *
 * <p>真发前有两道闸：
 * <ol>
 *   <li>收件人黑名单（{@code emailBlacklist}，大小写不敏感精确匹配）：命中不真发、仅日志，
 *       按发送成功处理且不占配额。用于测试环境屏蔽真人邮箱。</li>
 *   <li>全局当日发送量闸（{@code dailyLimit}，&lt;=0 关闭）：Redis 原子占位，
 *       真实发出量硬上限恰为 dailyLimit，防脚本刷爆 / 第三方账单失控。Redis 异常时降级放行（不阻断邮件）。</li>
 * </ol>
 */
public class EmailChannelAdapter implements ChannelAdapter {

    private static final Logger log = LoggerFactory.getLogger(EmailChannelAdapter.class);

    /** 当日计数 key 前缀，按自然日（服务器时区）分桶：{@code wen-saas-boot:email:sent:yyyy-MM-dd}。 */
    private static final String DAILY_KEY_PREFIX = "wen-saas-boot:email:sent:";

    private final JavaMailSender mailSender;
    private final boolean noop;
    private final String from;
    private final StringRedisTemplate redis;
    private final int dailyLimit;
    private final Set<String> blacklist;

    public EmailChannelAdapter(JavaMailSender mailSender, boolean noop, String from,
                               StringRedisTemplate redis, int dailyLimit,
                               Collection<String> blacklist) {
        this.mailSender = mailSender;
        this.noop = noop;
        this.from = from;
        this.redis = redis;
        this.dailyLimit = dailyLimit;
        this.blacklist = blacklist == null ? Set.of()
                : blacklist.stream()
                        .filter(s -> s != null && !s.isBlank())
                        .map(s -> s.trim().toLowerCase(Locale.ROOT))
                        .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public Channel channel() {
        return Channel.EMAIL;
    }

    @Override
    public boolean canDeliver(Recipient recipient) {
        return recipient.email() != null && !recipient.email().isBlank();
    }

    @Override
    public DeliveryResult deliver(RenderedMessage message, Recipient recipient) {
        if (noop) {
            log.info("[notify:email:noop] to={} subject={} body={}",
                    recipient.email(), message.title(), trim(message.body()));
            return DeliveryResult.sent();
        }
        if (blacklist.contains(recipient.email().trim().toLowerCase(Locale.ROOT))) {
            log.info("[notify:email:blacklist] 收件人在黑名单，不真发 to={} subject={}",
                    recipient.email(), message.title());
            return DeliveryResult.sent();
        }
        if (!reserveDailyQuota(message, recipient)) {
            return DeliveryResult.failed("EMAIL_DAILY_LIMIT_EXCEEDED");
        }
        SimpleMailMessage mail = new SimpleMailMessage();
        if (from != null && !from.isBlank()) {
            mail.setFrom(from);
        }
        mail.setTo(recipient.email());
        mail.setSubject(message.title() == null ? "" : message.title());
        mail.setText(message.body());
        mailSender.send(mail);
        return DeliveryResult.sent();
    }

    /**
     * 全局当日发送量闸：原子 INCR 占位，超限即拒（返回 false）。占位在发信前完成，
     * 保证真实发出量硬上限恰为 dailyLimit。dailyLimit&lt;=0 关闭；Redis 异常降级放行。
     */
    private boolean reserveDailyQuota(RenderedMessage message, Recipient recipient) {
        if (dailyLimit <= 0) {
            return true;
        }
        try {
            String key = DAILY_KEY_PREFIX + LocalDate.now();
            Long n = redis.opsForValue().increment(key);
            if (n != null && n == 1L) {
                redis.expire(key, Duration.ofDays(2)); // 覆盖当日 + 跨日兜底，自然日滚动
            }
            if (n != null && n > dailyLimit) {
                log.warn("[notify:email] 当日发送量已达上限 {}，拒发 to={} subject={}",
                        dailyLimit, recipient.email(), message.title());
                return false;
            }
            return true;
        } catch (RuntimeException e) {
            // Redis 异常不阻断邮件（与公开端点限流一致的降级放行策略）
            log.warn("[notify:email] 当日发送量闸检查失败，降级放行: {}", e.toString());
            return true;
        }
    }

    private static String trim(String s) {
        if (s == null) return "";
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }
}
