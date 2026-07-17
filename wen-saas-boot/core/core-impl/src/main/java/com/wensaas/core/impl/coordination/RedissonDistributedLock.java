package com.wensaas.core.impl.coordination;

import com.wensaas.core.api.lock.DistributedLock;
import com.wensaas.core.api.lock.LockAcquireException;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 基于 Redisson {@link RLock} 的分布式锁实现。
 *
 * <p>不传 leaseTime 的重载走 Redisson watchdog（持有期间后台自动续期）；传 leaseTime 的走固定租期。
 * 所有重载在同一线程内「获取—执行—释放」，{@code finally} 中以 {@link RLock#isHeldByCurrentThread()}
 * 守卫释放，避免固定租期到期后再 {@code unlock} 抛 {@code IllegalMonitorStateException}。
 */
public class RedissonDistributedLock implements DistributedLock {

    /** 锁 key 命名空间前缀，与全仓 Redis key 约定一致。 */
    static final String KEY_PREFIX = "wen-saas-boot:lock:";

    private final RedissonClient redisson;

    public RedissonDistributedLock(RedissonClient redisson) {
        this.redisson = redisson;
    }

    @Override
    public <T> T executeWithLock(String key, Supplier<T> action) {
        RLock lock = redisson.getLock(KEY_PREFIX + key);
        lock.lock(); // watchdog 自动续期
        try {
            return action.get();
        } finally {
            unlockIfHeld(lock);
        }
    }

    @Override
    public void executeWithLock(String key, Runnable action) {
        executeWithLock(key, () -> {
            action.run();
            return null;
        });
    }

    @Override
    public <T> T executeWithLock(String key, Duration leaseTime, Supplier<T> action) {
        RLock lock = redisson.getLock(KEY_PREFIX + key);
        lock.lock(leaseTime.toMillis(), TimeUnit.MILLISECONDS); // 固定租期、无续期
        try {
            return action.get();
        } finally {
            unlockIfHeld(lock);
        }
    }

    @Override
    public void executeWithLock(String key, Duration leaseTime, Runnable action) {
        executeWithLock(key, leaseTime, () -> {
            action.run();
            return null;
        });
    }

    @Override
    public <T> Optional<T> tryExecuteWithLock(String key, Duration waitTime, Supplier<T> action) {
        RLock lock = redisson.getLock(KEY_PREFIX + key);
        if (!tryLock(lock, key, waitTime)) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(action.get());
        } finally {
            unlockIfHeld(lock);
        }
    }

    @Override
    public boolean tryExecuteWithLock(String key, Duration waitTime, Runnable action) {
        RLock lock = redisson.getLock(KEY_PREFIX + key);
        if (!tryLock(lock, key, waitTime)) {
            return false;
        }
        try {
            action.run();
            return true;
        } finally {
            unlockIfHeld(lock);
        }
    }

    /** 有界等待获取（watchdog 自动续期）；中断转为 {@link LockAcquireException}。 */
    private boolean tryLock(RLock lock, String key, Duration waitTime) {
        try {
            return lock.tryLock(waitTime.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LockAcquireException("获取分布式锁被中断 key=" + key, e);
        }
    }

    private void unlockIfHeld(RLock lock) {
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
}
