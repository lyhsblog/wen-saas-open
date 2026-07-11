package com.wensaas.core.impl.auth.stepup;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.UUID;

/**
 * 敏感操作 step-up 票据 Redis 存储。Key 形如 {@code wen-saas-boot:stepup:{token}}，value=用户 ID。
 *
 * <p>验证身份通过后签发，值绑定 userId 防跨用户盗用；改密码 / 改手机 / 改邮箱的最终变更调用凭票据核销。
 * 改联系方式的「发新目标验证码」中间步只校验票据存在（{@link #ownerOf} 非空），不核销。
 */
public class StepUpTicketStore {

    static final String KEY_PREFIX = "wen-saas-boot:stepup:";

    private final StringRedisTemplate redis;
    private final Duration ttl;

    public StepUpTicketStore(StringRedisTemplate redis, Duration ttl) {
        this.redis = redis;
        this.ttl = ttl;
    }

    /** 签发票据，返回 token。 */
    public String issue(long userId) {
        String token = UUID.randomUUID().toString();
        redis.opsForValue().set(keyOf(token), Long.toString(userId), ttl);
        return token;
    }

    /** 返回票据所属 userId；不存在 / 过期返回 null。不删除。 */
    public Long ownerOf(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String raw = redis.opsForValue().get(keyOf(token));
        if (raw == null) {
            return null;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 核销（删除）票据。 */
    public void consume(String token) {
        if (token != null && !token.isBlank()) {
            redis.delete(keyOf(token));
        }
    }

    public Duration ttl() {
        return ttl;
    }

    private static String keyOf(String token) {
        return KEY_PREFIX + token;
    }
}
