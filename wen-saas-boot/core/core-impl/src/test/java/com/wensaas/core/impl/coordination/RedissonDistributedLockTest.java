package com.wensaas.core.impl.coordination;

import com.wensaas.core.api.lock.LockAcquireException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link RedissonDistributedLock} 纯单测：RLock 全 mock，验证 watchdog/固定租期获取、key 维度、
 * 有界等待获取失败短路、以及异常路径下的安全释放。
 */
class RedissonDistributedLockTest {

    private RedissonClient redisson;
    private RLock lock;
    private RedissonDistributedLock distributedLock;

    @BeforeEach
    void setUp() {
        redisson = mock(RedissonClient.class);
        lock = mock(RLock.class);
        when(redisson.getLock(anyString())).thenReturn(lock);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        distributedLock = new RedissonDistributedLock(redisson);
    }

    @Test
    void executeWithLock_watchdog_locksWithNamespacedKey_returnsResult_andUnlocks() {
        String result = distributedLock.executeWithLock("import:42", () -> "done");

        assertThat(result).isEqualTo("done");
        verify(redisson).getLock("wen-saas-boot:lock:import:42");
        verify(lock).lock();
        verify(lock).unlock();
    }

    @Test
    void executeWithLock_actionThrows_stillUnlocks() {
        assertThatThrownBy(() -> distributedLock.executeWithLock("k", () -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        verify(lock).unlock();
    }

    @Test
    void executeWithLock_fixedLease_usesLeaseTime() {
        distributedLock.executeWithLock("k", Duration.ofSeconds(30), () -> "x");
        verify(lock).lock(30_000L, TimeUnit.MILLISECONDS);
        verify(lock).unlock();
    }

    @Test
    void tryExecuteWithLock_acquired_runsAction_returnsPresent() throws InterruptedException {
        when(lock.tryLock(anyLong(), any())).thenReturn(true);

        Optional<String> r = distributedLock.tryExecuteWithLock("k", Duration.ZERO, () -> "ok");

        assertThat(r).contains("ok");
        verify(lock).unlock();
    }

    @Test
    void tryExecuteWithLock_notAcquired_skipsAction_returnsEmpty_noUnlock() throws InterruptedException {
        when(lock.tryLock(anyLong(), any())).thenReturn(false);
        AtomicBoolean ran = new AtomicBoolean(false);

        Optional<String> r = distributedLock.tryExecuteWithLock("k", Duration.ofSeconds(1), () -> {
            ran.set(true);
            return "ok";
        });

        assertThat(r).isEmpty();
        assertThat(ran).isFalse();
        verify(lock, never()).unlock();
    }

    @Test
    void tryExecuteWithLock_runnable_acquired_returnsTrue() throws InterruptedException {
        when(lock.tryLock(anyLong(), any())).thenReturn(true);
        AtomicBoolean ran = new AtomicBoolean(false);

        boolean executed = distributedLock.tryExecuteWithLock("k", Duration.ZERO, () -> ran.set(true));

        assertThat(executed).isTrue();
        assertThat(ran).isTrue();
        verify(lock).unlock();
    }

    @Test
    void tryExecuteWithLock_interrupted_throwsLockAcquireException_andRestoresInterrupt() throws InterruptedException {
        when(lock.tryLock(anyLong(), any())).thenThrow(new InterruptedException());

        try {
            assertThatThrownBy(() ->
                    distributedLock.tryExecuteWithLock("k", Duration.ofSeconds(1), () -> "x"))
                    .isInstanceOf(LockAcquireException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            // 清理中断标志，避免污染后续测试
            Thread.interrupted();
        }
    }
}
