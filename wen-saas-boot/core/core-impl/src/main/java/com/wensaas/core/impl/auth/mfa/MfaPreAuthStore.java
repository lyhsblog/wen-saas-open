package com.wensaas.core.impl.auth.mfa;

import com.alibaba.fastjson2.JSON;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * 预认证票据（{@link MfaPreAuth}）Redis 存储。Key 形如 {@code wen-saas-boot:mfa-preauth:{token}}。
 *
 * <p>票据在 setup→verify 跨多个公开端点存活，故用 GET（非 GETDEL）查询，校验通过后显式 consume。
 */
public class MfaPreAuthStore {

    static final String KEY_PREFIX = "wen-saas-boot:mfa-preauth:";

    private final StringRedisTemplate redis;
    private final Duration ttl;

    public MfaPreAuthStore(StringRedisTemplate redis, Duration ttl) {
        this.redis = redis;
        this.ttl = ttl;
    }

    public String create(MfaPreAuth preAuth) {
        String token = UUID.randomUUID().toString();
        redis.opsForValue().set(keyOf(token), JSON.toJSONString(preAuth), ttl);
        return token;
    }

    public Optional<MfaPreAuth> find(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String raw = redis.opsForValue().get(keyOf(token));
        return raw == null ? Optional.empty() : Optional.of(JSON.parseObject(raw, MfaPreAuth.class));
    }

    public void update(String token, MfaPreAuth preAuth) {
        redis.opsForValue().set(keyOf(token), JSON.toJSONString(preAuth), ttl);
    }

    public void consume(String token) {
        redis.delete(keyOf(token));
    }

    private static String keyOf(String token) {
        return KEY_PREFIX + token;
    }
}
