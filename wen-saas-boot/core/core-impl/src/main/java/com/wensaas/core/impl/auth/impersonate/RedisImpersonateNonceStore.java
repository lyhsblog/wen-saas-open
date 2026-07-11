package com.wensaas.core.impl.auth.impersonate;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Redis 代登录 nonce 存储。Key 形如 {@code wen-saas-boot:impersonate-nonce:{nonce}}，TTL=5min。
 *
 * <p>兑换使用 {@link StringRedisTemplate#opsForValue()#getAndDelete} 原子操作（Spring Data Redis 2.6+ /
 * Redis 6.2 GETDEL），保证单次兑换。
 */
public class RedisImpersonateNonceStore implements ImpersonateNonceStore {

    static final String KEY_PREFIX = "wen-saas-boot:impersonate-nonce:";
    static final Duration NONCE_TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate redis;

    public RedisImpersonateNonceStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public String issue(ImpersonateNonceData data) {
        String nonce = UUID.randomUUID().toString();
        redis.opsForValue().set(keyOf(nonce), serialize(data), NONCE_TTL);
        return nonce;
    }

    @Override
    public Optional<ImpersonateNonceData> redeem(String nonce) {
        if (nonce == null || nonce.isBlank()) {
            return Optional.empty();
        }
        String raw = redis.opsForValue().getAndDelete(keyOf(nonce));
        if (raw == null) {
            return Optional.empty();
        }
        return Optional.of(deserialize(raw));
    }

    static String keyOf(String nonce) {
        return KEY_PREFIX + nonce;
    }

    static String serialize(ImpersonateNonceData d) {
        JSONObject jo = new JSONObject(5);
        jo.put("targetTenantId", d.targetTenantId());
        jo.put("asUserId", d.asUserId());
        jo.put("operatorUserId", d.operatorUserId());
        jo.put("operatorSessionId", d.operatorSessionId());
        jo.put("createdAt", d.createdAt().toString());
        return jo.toJSONString();
    }

    static ImpersonateNonceData deserialize(String json) {
        try {
            JSONObject jo = JSON.parseObject(json);
            return new ImpersonateNonceData(
                    jo.getLongValue("targetTenantId"),
                    jo.getLongValue("asUserId"),
                    jo.getLongValue("operatorUserId"),
                    jo.getString("operatorSessionId"),
                    Instant.parse(jo.getString("createdAt")));
        } catch (RuntimeException e) {
            throw new IllegalStateException("ImpersonateNonceData JSON 反序列化失败：" + json, e);
        }
    }
}
