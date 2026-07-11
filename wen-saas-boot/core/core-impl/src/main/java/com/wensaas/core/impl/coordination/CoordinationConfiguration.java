package com.wensaas.core.impl.coordination;

import com.wensaas.core.api.cache.CacheRegionFactory;
import com.wensaas.core.api.idempotent.IdempotencyGuard;
import com.wensaas.core.api.lock.DistributedLock;
import com.wensaas.core.impl.cache.RedissonCacheRegionFactory;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 分布式协调原语装配（架构审查 M1：统一 Redis 锁 + 去重基建）。
 *
 * <p>{@link RedissonClient} 由 redisson-spring-boot-starter 自动装配，{@link StringRedisTemplate}
 * 由 spring-data-redis 自动装配；此处仅把它们包装成底座对外的框架无关契约
 * （{@link DistributedLock} / {@link IdempotencyGuard}）。
 *
 * <p>{@code @ConditionalOnMissingBean} 让下游业务可按需替换实现而不冲突。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CacheProperties.class)
public class CoordinationConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public DistributedLock distributedLock(RedissonClient redisson) {
        return new RedissonDistributedLock(redisson);
    }

    @Bean
    @ConditionalOnMissingBean
    public IdempotencyGuard idempotencyGuard(StringRedisTemplate redis) {
        return new RedisIdempotencyGuard(redis);
    }

    @Bean
    @ConditionalOnMissingBean
    public SlidingWindowRateLimiter slidingWindowRateLimiter(StringRedisTemplate redis) {
        return new SlidingWindowRateLimiter(redis);
    }

    @Bean
    @ConditionalOnMissingBean
    public CacheRegionFactory cacheRegionFactory(RedissonClient redisson, DistributedLock lock,
                                                  CacheProperties props) {
        return new RedissonCacheRegionFactory(redisson, lock, props.getRegionLocalCacheSize(),
                props.getSingleFlightWaitTime());
    }
}
