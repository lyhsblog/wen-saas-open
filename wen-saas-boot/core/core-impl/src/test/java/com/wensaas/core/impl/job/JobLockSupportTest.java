package com.wensaas.core.impl.job;

import com.wensaas.core.api.lock.DistributedLock;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link JobLockSupport} 纯单测：验证 key 前缀、立即尝试（Duration.ZERO）、拿不到锁则跳过。
 */
class JobLockSupportTest {

    @Test
    void runExclusive_lockAcquired_runsAction_withPrefixedKeyAndZeroWait() {
        DistributedLock lock = mock(DistributedLock.class);
        // 模拟拿到锁：执行传入的 action 并返回 true
        doReturn(true).when(lock).tryExecuteWithLock(eq("job:tenant-expire"),
                eq(Duration.ZERO), any(Runnable.class));

        JobLockSupport support = new JobLockSupport(lock);
        support.runExclusive("tenant-expire", () -> { });

        verify(lock).tryExecuteWithLock(eq("job:tenant-expire"), eq(Duration.ZERO), any(Runnable.class));
    }

    @Test
    void runExclusive_lockAcquired_actuallyExecutesAction() {
        AtomicBoolean ran = new AtomicBoolean(false);
        DistributedLock lock = mock(DistributedLock.class);
        // 锁回放 action，确认 support 把 action 透传给了锁
        when(lock.tryExecuteWithLock(any(), any(Duration.class), any(Runnable.class)))
                .thenAnswer(inv -> {
                    inv.<Runnable>getArgument(2).run();
                    return true;
                });

        new JobLockSupport(lock).runExclusive("x", () -> ran.set(true));

        assertThat(ran).isTrue();
    }

    @Test
    void runExclusive_lockNotAcquired_skipsSilently() {
        AtomicBoolean ran = new AtomicBoolean(false);
        DistributedLock lock = mock(DistributedLock.class);
        // 拿不到锁：返回 false 且不回放 action
        doReturn(false).when(lock).tryExecuteWithLock(any(), any(Duration.class), any(Runnable.class));

        JobLockSupport support = new JobLockSupport(lock);
        support.runExclusive("tenant-expire", () -> ran.set(true));

        // 未抛异常；action 未执行
        assertThat(ran).isFalse();
        verify(lock).tryExecuteWithLock(eq("job:tenant-expire"), eq(Duration.ZERO), any(Runnable.class));
    }
}
