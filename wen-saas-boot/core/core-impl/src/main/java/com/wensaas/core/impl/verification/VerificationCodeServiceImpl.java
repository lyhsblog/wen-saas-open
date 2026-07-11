package com.wensaas.core.impl.verification;

import com.wensaas.core.api.notification.send.Channel;
import com.wensaas.core.api.notification.send.ChannelAdapter;
import com.wensaas.core.api.notification.send.DeliveryResult;
import com.wensaas.core.api.notification.send.MessageCategory;
import com.wensaas.core.api.notification.send.MessageStatus;
import com.wensaas.core.api.notification.send.Recipient;
import com.wensaas.core.api.notification.template.RenderedMessage;
import com.wensaas.core.api.verification.VerificationCodeService;
import com.wensaas.core.api.verification.VerificationException;
import com.wensaas.core.api.verification.VerificationPurpose;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 验证码服务 Redis 实现。
 *
 * <p>键设计（前缀 {@code wen-saas-boot:otp:}）：
 * <ul>
 *   <li>{@code code:{purpose}:{target}} — hash{h=码SHA256, a=失败次数}，TTL=ttl</li>
 *   <li>{@code cd:{purpose}:{target}}  — 重发冷却，TTL=resendInterval</li>
 *   <li>{@code daily:{purpose}:{target}} — 当日计数，TTL=24h</li>
 * </ul>
 * 码以 SHA-256 存储（防 Redis dump 直读）；校验/失败计数/作废用 Lua 原子完成。
 */
public class VerificationCodeServiceImpl implements VerificationCodeService {

    private static final Logger log = LoggerFactory.getLogger(VerificationCodeServiceImpl.class);

    private static final String KEY_PREFIX = "wen-saas-boot:otp:";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /** HSET 码哈希 + 清零失败计数 + EXPIRE，原子。 */
    private static final DefaultRedisScript<Long> STORE_SCRIPT = new DefaultRedisScript<>(
            """
            redis.call('HSET', KEYS[1], 'h', ARGV[1], 'a', '0')
            redis.call('EXPIRE', KEYS[1], ARGV[2])
            return 1
            """,
            Long.class);

    /** 校验：命中删除返回 OK；未命中 MISS；错误自增失败数，达上限删除，返回 BAD。 */
    private static final DefaultRedisScript<String> VERIFY_SCRIPT = new DefaultRedisScript<>(
            """
            local h = redis.call('HGET', KEYS[1], 'h')
            if h == false then return 'MISS' end
            if h == ARGV[1] then
                redis.call('DEL', KEYS[1])
                return 'OK'
            end
            local a = redis.call('HINCRBY', KEYS[1], 'a', 1)
            if a >= tonumber(ARGV[2]) then
                redis.call('DEL', KEYS[1])
            end
            return 'BAD'
            """,
            String.class);

    /** 当日计数 INCR + 首次 EXPIRE。 */
    private static final DefaultRedisScript<Long> DAILY_SCRIPT = new DefaultRedisScript<>(
            """
            local c = redis.call('INCR', KEYS[1])
            if c == 1 then
                redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            return c
            """,
            Long.class);

    private final StringRedisTemplate redis;
    private final Map<Channel, ChannelAdapter> adapters;
    private final VerificationCodeProperties props;

    public VerificationCodeServiceImpl(StringRedisTemplate redis,
                                       Map<Channel, ChannelAdapter> adapters,
                                       VerificationCodeProperties props) {
        this.redis = redis;
        this.adapters = adapters;
        this.props = props;
    }

    @Override
    public SentResult send(VerificationPurpose purpose, Channel channel, String target,
                           long userId, long tenantId) {
        if (target == null || target.isBlank()) {
            throw new VerificationException(VerificationException.Code.TARGET_REQUIRED, "目标为空");
        }
        ChannelAdapter adapter = adapters.get(channel);
        if (adapter == null) {
            throw new VerificationException(VerificationException.Code.CHANNEL_UNAVAILABLE,
                    "通道不可用: " + channel);
        }

        // 1. 重发冷却
        String cdKey = KEY_PREFIX + "cd:" + purpose + ":" + target;
        Long ttlLeft = redis.getExpire(cdKey, TimeUnit.SECONDS);
        if (Boolean.TRUE.equals(redis.hasKey(cdKey)) && ttlLeft != null && ttlLeft > 0) {
            throw new VerificationException(VerificationException.Code.RATE_LIMITED,
                    "发送过于频繁，请 " + ttlLeft + " 秒后重试", ttlLeft.intValue());
        }

        // 2. 当日上限
        String dailyKey = KEY_PREFIX + "daily:" + purpose + ":" + target;
        Long dailyCount = redis.execute(DAILY_SCRIPT, List.of(dailyKey),
                String.valueOf(Duration.ofDays(1).toSeconds()));
        if (dailyCount != null && dailyCount > props.getDailyLimit()) {
            throw new VerificationException(VerificationException.Code.DAILY_LIMIT_EXCEEDED,
                    "今日验证码发送次数已达上限");
        }

        // 3. 生成 + 存储
        String code = randomCode(props.getCodeLength());
        String codeKey = KEY_PREFIX + "code:" + purpose + ":" + target;
        redis.execute(STORE_SCRIPT, List.of(codeKey), sha256(code),
                String.valueOf(props.getTtl().toSeconds()));

        // 4. 投递（同步）
        Recipient recipient = channel == Channel.SMS
                ? new Recipient(userId, tenantId, null, target)
                : new Recipient(userId, tenantId, target, null);
        if (!adapter.canDeliver(recipient)) {
            throw new VerificationException(VerificationException.Code.CHANNEL_UNAVAILABLE,
                    "收件人不满足通道要求: " + channel);
        }
        long minutes = Math.max(1, props.getTtl().toMinutes());
        String body = "验证码 " + code + "，" + minutes + " 分钟内有效，请勿向他人泄露。";
        RenderedMessage message = new RenderedMessage(tenantId, UUID.randomUUID().toString(),
                channel, "auth.otp", MessageCategory.SYSTEM, null, "验证码", body,
                null, null, null, "auth", Map.of("code", code));
        DeliveryResult result = adapter.deliver(message, recipient);
        if (result.status() != MessageStatus.SENT) {
            throw new VerificationException(VerificationException.Code.DELIVERY_FAILED,
                    "验证码投递失败: " + result.errorReason());
        }

        // 5. 设置重发冷却
        redis.opsForValue().set(cdKey, "1", props.getResendInterval());
        log.info("otp.sent purpose={} channel={} target={} userId={}",
                purpose, channel, mask(target), userId);
        return new SentResult(mask(target), (int) props.getTtl().toSeconds(),
                (int) props.getResendInterval().toSeconds());
    }

    @Override
    public boolean verify(VerificationPurpose purpose, String target, String code) {
        if (target == null || target.isBlank() || code == null || code.isBlank()) {
            return false;
        }
        String codeKey = KEY_PREFIX + "code:" + purpose + ":" + target;
        String r = redis.execute(VERIFY_SCRIPT, List.of(codeKey), sha256(code),
                String.valueOf(props.getMaxVerifyAttempts()));
        return "OK".equals(r);
    }

    private static String randomCode(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(SECURE_RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    /** 手机号：前 3 + **** + 后 4；邮箱：首字符 + *** + @域名；其余截断。 */
    static String mask(String target) {
        if (target == null || target.isBlank()) {
            return "";
        }
        int at = target.indexOf('@');
        if (at > 0) {
            String local = target.substring(0, at);
            String head = local.isEmpty() ? "" : local.substring(0, 1);
            return head + "***" + target.substring(at);
        }
        if (target.length() >= 7) {
            return target.substring(0, 3) + "****" + target.substring(target.length() - 4);
        }
        return target.charAt(0) + "****";
    }
}
