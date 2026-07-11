package com.wensaas.core.impl.openapi.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import com.wensaas.core.impl.openapi.OpenApiProperties;

/**
 * 基于 Redis Sorted Set + Lua 脚本的滑动窗口限流。
 *
 * <p>Key 设计：v1 现役 {@code wen-saas-boot:openapi:ratelimit:{tenantId}}；
 * 调用方传 {@code apiKeyId != null} 时拼成 {@code :{tenantId}:{apiKeyId}}，
 * 让 P1 凭证级限流可零切换升级。
 *
 * <p>Lua 失败 / Redis 故障时按"放行 + log.warn"降级（限流功能可用性 > 严格性）。
 */
public class RedisSlidingWindowRateLimiter implements RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisSlidingWindowRateLimiter.class);

    private final StringRedisTemplate redis;
    private final OpenApiProperties props;
    private final RedisScript<List> script;

    @SuppressWarnings({"rawtypes", "unchecked"})
    public RedisSlidingWindowRateLimiter(StringRedisTemplate redis, OpenApiProperties props) {
        this.redis = redis;
        this.props = props;
        DefaultRedisScript s = new DefaultRedisScript();
        s.setLocation(new ClassPathResource("scripts/rate_limit.lua"));
        s.setResultType(List.class);
        this.script = s;
    }

    @Override
    public Decision tryAcquire(long tenantId, Long apiKeyId, Integer overrideLimit) {
        OpenApiProperties.RateLimit cfg = props.getRateLimit();
        if (!cfg.isEnabled()) {
            return new Decision(true, Integer.MAX_VALUE, Integer.MAX_VALUE,
                    Instant.now().getEpochSecond() + cfg.getWindow().toSeconds());
        }
        // overrideLimit 非空（凭证级，取凭证 rate_limit_qps）优先；否则按租户配置
        int limit = overrideLimit != null ? overrideLimit : cfg.limitFor(tenantId);
        long windowMs = cfg.getWindow().toMillis();
        long nowMs = System.currentTimeMillis();
        String key = buildKey(cfg.getKeyPrefix(), tenantId, apiKeyId);
        String member = nowMs + "-" + UUID.randomUUID();

        long resetEpoch = (nowMs + windowMs) / 1000L;
        try {
            @SuppressWarnings("unchecked")
            List<Long> result = redis.execute(script,
                    Collections.singletonList(key),
                    String.valueOf(nowMs),
                    String.valueOf(windowMs),
                    String.valueOf(limit),
                    member);
            if (result == null || result.size() < 2) {
                log.warn("ratelimit.script.fail key={} result={}", key, result);
                return new Decision(true, limit, limit, resetEpoch);
            }
            boolean allowed = result.get(0) != null && result.get(0) == 1L;
            int remaining = result.get(1) == null ? 0 : result.get(1).intValue();
            return new Decision(allowed, limit, remaining, resetEpoch);
        } catch (RuntimeException e) {
            log.warn("ratelimit.redis.fail key={} reason={}", key, e.getMessage());
            return new Decision(true, limit, limit, resetEpoch);
        }
    }

    static String buildKey(String prefix, long tenantId, Long apiKeyId) {
        if (apiKeyId == null) {
            return prefix + ":" + tenantId;
        }
        return prefix + ":" + tenantId + ":" + apiKeyId;
    }
}
