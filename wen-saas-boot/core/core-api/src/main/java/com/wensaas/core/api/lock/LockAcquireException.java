package com.wensaas.core.api.lock;

/**
 * 获取分布式锁失败（当前仅在等待获取过程中线程被中断时抛出）。
 *
 * <p>未获取到锁的「正常」结果由 {@code tryExecuteWithLock} 的返回值表达，不走异常。
 */
public class LockAcquireException extends RuntimeException {

    public LockAcquireException(String message, Throwable cause) {
        super(message, cause);
    }
}
