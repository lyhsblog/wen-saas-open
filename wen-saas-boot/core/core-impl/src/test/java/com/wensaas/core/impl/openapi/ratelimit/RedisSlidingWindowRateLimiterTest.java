package com.wensaas.core.impl.openapi.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.wensaas.core.impl.openapi.OpenApiProperties;

/**
 * 验证限额取值（凭证级 overrideLimit 优先于租户级配置）与 key 维度。
 * Redis 交互被 mock，仅断言喂给 Lua 的 limit ARGV 与 key。
 */
class RedisSlidingWindowRateLimiterTest {

    private StringRedisTemplate redis;
    private OpenApiProperties props;
    private RedisSlidingWindowRateLimiter limiter;

    @BeforeEach
    @SuppressWarnings({"unchecked", "rawtypes"})
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        props = new OpenApiProperties();
        props.getRateLimit().setDefaultLimit(1000);
        props.getRateLimit().getTenantOverrides().put(100L, 800);
        limiter = new RedisSlidingWindowRateLimiter(redis, props);
        when(redis.execute(any(RedisScript.class), anyList(), any(), any(), any(), any()))
                .thenReturn((List) List.of(1L, 7L));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<Object> captureArgs() {
        ArgumentCaptor<Object> keys = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> args = ArgumentCaptor.forClass(Object.class);
        verify(redis).execute(any(RedisScript.class), (List) keys.capture(),
                args.capture(), args.capture(), args.capture(), args.capture());
        return args.getAllValues();   // [now, window, limit, member]
    }

    private String capturedKey() {
        ArgumentCaptor<List> keys = ArgumentCaptor.forClass(List.class);
        verify(redis).execute(any(RedisScript.class), keys.capture(),
                any(), any(), any(), any());
        return (String) keys.getValue().get(0);
    }

    @Test
    void tenantLevel_usesTenantConfiguredLimit() {
        RateLimiter.Decision d = limiter.tryAcquire(100L, null);
        assertThat(d.allowed()).isTrue();
        assertThat(d.limit()).isEqualTo(800);            // tenantOverrides 命中
        assertThat(captureArgs().get(2)).isEqualTo("800");
        assertThat(capturedKey()).isEqualTo("wen-saas-boot:openapi:ratelimit:100");
    }

    @Test
    void credentialLevel_usesOverrideLimit_andTwoDimKey() {
        RateLimiter.Decision d = limiter.tryAcquire(100L, 555L, 50);
        assertThat(d.limit()).isEqualTo(50);             // overrideLimit 优先
        assertThat(captureArgs().get(2)).isEqualTo("50");
        assertThat(capturedKey()).isEqualTo("wen-saas-boot:openapi:ratelimit:100:555");
    }

    @Test
    void disabled_allowsWithoutRedis() {
        props.getRateLimit().setEnabled(false);
        RateLimiter.Decision d = limiter.tryAcquire(100L, 555L, 50);
        assertThat(d.allowed()).isTrue();
        assertThat(d.limit()).isEqualTo(Integer.MAX_VALUE);
    }
}
