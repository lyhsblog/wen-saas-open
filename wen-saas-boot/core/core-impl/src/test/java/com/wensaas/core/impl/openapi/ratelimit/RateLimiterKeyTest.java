package com.wensaas.core.impl.openapi.ratelimit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 v1 仅租户级 + 凭证级 Redis key 结构预留契约。
 */
class RateLimiterKeyTest {

    private static final String PREFIX = "wen-saas-boot:openapi:ratelimit";

    @Test
    void tenantLevelKey() {
        assertThat(RedisSlidingWindowRateLimiter.buildKey(PREFIX, 100L, null))
                .isEqualTo("wen-saas-boot:openapi:ratelimit:100");
    }

    @Test
    void apiKeyLevelKeyReservesForV2() {
        assertThat(RedisSlidingWindowRateLimiter.buildKey(PREFIX, 100L, 555L))
                .isEqualTo("wen-saas-boot:openapi:ratelimit:100:555");
    }
}
