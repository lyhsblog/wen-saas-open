package com.wensaas.core.impl.coordination;

import com.wensaas.core.api.idempotent.IdempotencyGuard;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/**
 * 基于 Redis {@code SET key val NX EX ttl} 的幂等去重实现（架构审查 M1 / M6）。
 *
 * <p>{@link #firstSeen} 用 {@code setIfAbsent}（底层 SET NX）做原子声明；并发重复只有一个返回
 * {@code true}。{@link #runOnce} 封装「成功保留声明 / 失败回滚声明」，失败后允许 MQ 重投重新处理。
 *
 * <p>走 {@link StringRedisTemplate}（Redisson 作为唯一工厂提供 RedisConnectionFactory，模板用法不变）。
 */
public class RedisIdempotencyGuard implements IdempotencyGuard {

    /** 去重 key 命名空间前缀，与全仓 Redis key 约定一致。 */
    static final String KEY_PREFIX = "wen-saas-boot:dedup:";

    /** 声明占位值（内容无意义，仅取键的存在性）。 */
    private static final String MARKER = "1";

    private final StringRedisTemplate redis;

    public RedisIdempotencyGuard(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean firstSeen(String scope, String id, Duration ttl) {
        Boolean ok = redis.opsForValue().setIfAbsent(buildKey(scope, id), MARKER, ttl);
        return Boolean.TRUE.equals(ok);
    }

    @Override
    public void release(String scope, String id) {
        redis.delete(buildKey(scope, id));
    }

    @Override
    public boolean runOnce(String scope, String id, Duration ttl, Runnable action) {
        if (!firstSeen(scope, id, ttl)) {
            return false;
        }
        try {
            action.run();
            return true;
        } catch (RuntimeException e) {
            release(scope, id);
            throw e;
        }
    }

    static String buildKey(String scope, String id) {
        return KEY_PREFIX + scope + ":" + id;
    }
}
