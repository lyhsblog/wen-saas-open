package com.wensaas.core.impl.coordination;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link RedisIdempotencyGuard} 纯单测：Redis 交互全 mock，验证 SETNX 语义、key 维度与失败回滚。
 */
class RedisIdempotencyGuardTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOps;
    private RedisIdempotencyGuard guard;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        guard = new RedisIdempotencyGuard(redis);
    }

    @Test
    void firstSeen_setNxSucceeds_returnsTrue_withNamespacedKey() {
        when(valueOps.setIfAbsent(eq("wen-saas-boot:dedup:webhook-send:evt-1"), any(), any()))
                .thenReturn(true);

        boolean first = guard.firstSeen("webhook-send", "evt-1", Duration.ofMinutes(10));

        assertThat(first).isTrue();
        verify(valueOps).setIfAbsent("wen-saas-boot:dedup:webhook-send:evt-1", "1",
                Duration.ofMinutes(10));
    }

    @Test
    void firstSeen_setNxFails_returnsFalse() {
        when(valueOps.setIfAbsent(any(), any(), any())).thenReturn(false);
        assertThat(guard.firstSeen("s", "id", Duration.ofMinutes(1))).isFalse();
    }

    @Test
    void firstSeen_nullReply_treatedAsNotFirst() {
        when(valueOps.setIfAbsent(any(), any(), any())).thenReturn(null);
        assertThat(guard.firstSeen("s", "id", Duration.ofMinutes(1))).isFalse();
    }

    @Test
    void release_deletesNamespacedKey() {
        guard.release("webhook-send", "evt-1");
        verify(redis).delete("wen-saas-boot:dedup:webhook-send:evt-1");
    }

    @Test
    void runOnce_first_runsAction_returnsTrue() {
        when(valueOps.setIfAbsent(any(), any(), any())).thenReturn(true);
        AtomicBoolean ran = new AtomicBoolean(false);

        boolean executed = guard.runOnce("s", "id", Duration.ofMinutes(1), () -> ran.set(true));

        assertThat(executed).isTrue();
        assertThat(ran).isTrue();
        verify(redis, never()).delete(any(String.class));
    }

    @Test
    void runOnce_duplicate_skipsAction_returnsFalse() {
        when(valueOps.setIfAbsent(any(), any(), any())).thenReturn(false);
        AtomicBoolean ran = new AtomicBoolean(false);

        boolean executed = guard.runOnce("s", "id", Duration.ofMinutes(1), () -> ran.set(true));

        assertThat(executed).isFalse();
        assertThat(ran).isFalse();
    }

    @Test
    void runOnce_actionThrows_releasesClaim_andRethrows() {
        when(valueOps.setIfAbsent(any(), any(), any())).thenReturn(true);

        assertThatThrownBy(() -> guard.runOnce("s", "id", Duration.ofMinutes(1), () -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class).hasMessage("boom");

        verify(redis).delete("wen-saas-boot:dedup:s:id");
    }
}
