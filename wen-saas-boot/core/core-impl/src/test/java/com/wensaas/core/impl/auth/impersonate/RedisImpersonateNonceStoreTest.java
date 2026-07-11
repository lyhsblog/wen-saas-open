package com.wensaas.core.impl.auth.impersonate;

import com.wensaas.core.impl.auth.impersonate.ImpersonateNonceData;
import com.wensaas.core.impl.auth.impersonate.RedisImpersonateNonceStore;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisImpersonateNonceStoreTest {

    // ===== serialize / deserialize 纯单元测试（无 Redis） =====

    @Test
    void serialize_deserialize_roundTrip() {
        ImpersonateNonceData original = new ImpersonateNonceData(
                100L, 200L, 1001L, "ops-session-id",
                Instant.parse("2026-05-27T10:00:00Z"));

        String json = RedisImpersonateNonceStore.serialize(original);
        ImpersonateNonceData restored = RedisImpersonateNonceStore.deserialize(json);

        assertThat(restored.targetTenantId()).isEqualTo(100L);
        assertThat(restored.asUserId()).isEqualTo(200L);
        assertThat(restored.operatorUserId()).isEqualTo(1001L);
        assertThat(restored.operatorSessionId()).isEqualTo("ops-session-id");
        assertThat(restored.createdAt()).isEqualTo(Instant.parse("2026-05-27T10:00:00Z"));
    }

    @Test
    void serialize_producesExpectedKeys() {
        ImpersonateNonceData data = new ImpersonateNonceData(1L, 2L, 3L, "sid", Instant.now());
        String json = RedisImpersonateNonceStore.serialize(data);
        assertThat(json).contains("targetTenantId");
        assertThat(json).contains("operatorSessionId");
    }

    // ===== issue / redeem 使用 Mockito mock Redis =====

    @SuppressWarnings("unchecked")
    @Test
    void issue_storesJsonWithTtl_returnsNonBlankNonce() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);

        RedisImpersonateNonceStore store = new RedisImpersonateNonceStore(redis);
        ImpersonateNonceData data = new ImpersonateNonceData(100L, 200L, 1001L, "sid", Instant.now());

        String nonce = store.issue(data);

        assertThat(nonce).isNotBlank();
        verify(ops).set(
                eq(RedisImpersonateNonceStore.KEY_PREFIX + nonce),
                any(String.class),
                eq(RedisImpersonateNonceStore.NONCE_TTL));
    }

    @SuppressWarnings("unchecked")
    @Test
    void redeem_validNonce_returnsDataAndDeletesKey() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);

        ImpersonateNonceData data = new ImpersonateNonceData(
                100L, 200L, 1001L, "sid", Instant.parse("2026-05-27T10:00:00Z"));
        String serialized = RedisImpersonateNonceStore.serialize(data);
        when(ops.getAndDelete(RedisImpersonateNonceStore.KEY_PREFIX + "test-nonce"))
                .thenReturn(serialized);

        RedisImpersonateNonceStore store = new RedisImpersonateNonceStore(redis);
        Optional<ImpersonateNonceData> result = store.redeem("test-nonce");

        assertThat(result).isPresent();
        assertThat(result.get().targetTenantId()).isEqualTo(100L);
        assertThat(result.get().operatorSessionId()).isEqualTo("sid");
        verify(ops).getAndDelete(RedisImpersonateNonceStore.KEY_PREFIX + "test-nonce");
    }

    @SuppressWarnings("unchecked")
    @Test
    void redeem_expiredOrMissing_returnsEmpty() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.getAndDelete(any())).thenReturn(null);

        RedisImpersonateNonceStore store = new RedisImpersonateNonceStore(redis);

        assertThat(store.redeem("gone-nonce")).isEmpty();
        assertThat(store.redeem(null)).isEmpty();
        assertThat(store.redeem("  ")).isEmpty();
    }

    @SuppressWarnings("unchecked")
    @Test
    void redeem_singleUse_secondCallReturnsEmpty() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);

        ImpersonateNonceData data = new ImpersonateNonceData(100L, 200L, 1001L, "sid", Instant.now());
        String serialized = RedisImpersonateNonceStore.serialize(data);
        // 第一次 getAndDelete 返回值，第二次返回 null（模拟 key 已被删除）
        when(ops.getAndDelete(RedisImpersonateNonceStore.KEY_PREFIX + "one-shot"))
                .thenReturn(serialized)
                .thenReturn(null);

        RedisImpersonateNonceStore store = new RedisImpersonateNonceStore(redis);

        assertThat(store.redeem("one-shot")).isPresent();
        assertThat(store.redeem("one-shot")).isEmpty();
    }
}
