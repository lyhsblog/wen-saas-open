package com.wensaas.core.impl.coordination;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 通用滑动窗口限流（架构审查低优项：公开端点 IP 级频控）。
 *
 * <p>复用 {@code scripts/rate_limit.lua}（ZSet + Lua 原子操作），但按调用方任意 {@code key} 维度计数，
 * 不绑定开放 API 的租户 / 凭证语义。供「登录前 / public 未认证」等无业务身份的端点按 IP 限流复用。
 *
 * <p>Redis 故障 / Lua 失败时按「放行 + log.warn」降级（可用性 &gt; 严格性，与
 * {@code RedisSlidingWindowRateLimiter} 一致）。
 */
public class SlidingWindowRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(SlidingWindowRateLimiter.class);

    private final StringRedisTemplate redis;
    private final RedisScript<List> script;

    @SuppressWarnings({"rawtypes", "unchecked"})
    public SlidingWindowRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
        DefaultRedisScript s = new DefaultRedisScript();
        s.setLocation(new ClassPathResource("scripts/rate_limit.lua"));
        s.setResultType(List.class);
        this.script = s;
    }

    /**
     * 尝试在 {@code key} 维度的滑动窗口内占一个名额。
     *
     * @param key    限流维度键（调用方自带命名空间）
     * @param limit  窗口内允许的最大请求数
     * @param window 窗口长度
     */
    public Decision tryAcquire(String key, int limit, Duration window) {
        long windowMs = window.toMillis();
        long nowMs = System.currentTimeMillis();
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

    /** 限流决策。 */
    public record Decision(boolean allowed, int limit, int remaining, long resetEpochSecond) {
    }
}
