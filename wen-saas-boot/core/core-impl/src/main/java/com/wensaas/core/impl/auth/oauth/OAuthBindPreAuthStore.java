package com.wensaas.core.impl.auth.oauth;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * OAuth 绑定流临时凭证存储。Key 形如 {@code wen-saas-boot:oauth-bind:{token}}，TTL=5min。
 *
 * <p>与代登录 nonce（GETDEL 单次）不同：绑定提交时账密可能错误需重试，故 {@link #find} 用 GET（不删），
 * 仅在绑定成功后 {@link #consume} 删除，避免一次输错就要重新扫码。
 */
public class OAuthBindPreAuthStore {

    static final String KEY_PREFIX = "wen-saas-boot:oauth-bind:";
    static final Duration TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate redis;

    public OAuthBindPreAuthStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** 生成 UUID token，持久化渠道身份到 Redis（TTL=5min）。 */
    public String create(OAuthBindPreAuth data) {
        String token = UUID.randomUUID().toString();
        redis.opsForValue().set(keyOf(token), serialize(data), TTL);
        return token;
    }

    /** 读取（不删除）；token 不存在 / 已过期 / 已消费返回 empty。 */
    public Optional<OAuthBindPreAuth> find(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String raw = redis.opsForValue().get(keyOf(token));
        return raw == null ? Optional.empty() : Optional.of(deserialize(raw));
    }

    /** 绑定成功后删除，使 token 失效。 */
    public void consume(String token) {
        if (token != null && !token.isBlank()) {
            redis.delete(keyOf(token));
        }
    }

    static String keyOf(String token) {
        return KEY_PREFIX + token;
    }

    static String serialize(OAuthBindPreAuth d) {
        JSONObject jo = new JSONObject(6);
        jo.put("provider", d.provider());
        jo.put("externalId", d.externalId());
        jo.put("unionId", d.unionId());
        jo.put("nickname", d.nickname());
        jo.put("avatarUrl", d.avatarUrl());
        jo.put("createdAt", d.createdAt().toString());
        return jo.toJSONString();
    }

    static OAuthBindPreAuth deserialize(String json) {
        try {
            JSONObject jo = JSON.parseObject(json);
            return new OAuthBindPreAuth(
                    jo.getString("provider"),
                    jo.getString("externalId"),
                    jo.getString("unionId"),
                    jo.getString("nickname"),
                    jo.getString("avatarUrl"),
                    Instant.parse(jo.getString("createdAt")));
        } catch (RuntimeException e) {
            throw new IllegalStateException("OAuthBindPreAuth JSON 反序列化失败：" + json, e);
        }
    }
}
