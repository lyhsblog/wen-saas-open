package com.wensaas.core.impl.job;

import com.wensaas.core.api.lock.DistributedLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 定时任务「多实例单跑」守卫（架构审查 M5）。
 *
 * <p>Quartz JDBC 集群存储已保证一次触发只有一个节点认领，本守卫是执行体外的
 * <b>第二道兜底</b>——底座对外开源、下游部署不可控（两套部署误指不同 quartz schema、
 * 改回内存存储等形态都会退化成多实例各自触发）。{@link DistributedLock} 拿到锁才跑，
 * 拿不到（说明另一实例正在跑）直接跳过、不等待。
 *
 * <p>用 {@link DistributedLock#tryExecuteWithLock(String, Duration, Runnable)} +
 * {@link Duration#ZERO}（立即尝试一次）+ watchdog 自动续期（执行时长不可预估的清理/扫描类任务
 * 期间后台续约，结束后在 {@code finally} 释放）。锁 key 统一加 {@code job:} 业务前缀，实现再叠加
 * {@code wen-saas-boot:lock:} 命名空间。
 *
 * <p>与调度装配同门禁（{@code wen-saas-boot.jobs.enabled}，默认开）。
 */
@Component
@ConditionalOnProperty(prefix = "wen-saas-boot.jobs", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class JobLockSupport {

    private static final Logger log = LoggerFactory.getLogger(JobLockSupport.class);

    /** Job 锁 key 业务前缀（实现再叠加 {@code wen-saas-boot:lock:} 命名空间）。 */
    static final String KEY_PREFIX = "job:";

    private final DistributedLock lock;

    public JobLockSupport(DistributedLock lock) {
        this.lock = lock;
    }

    /**
     * 单实例执行：拿到锁则跑 {@code action}，拿不到锁（另一实例正在跑）直接跳过。
     *
     * @param jobKey 业务锁键（如 {@code "tenant-expire"}），自动加 {@code job:} 前缀
     * @param action 任务执行体
     */
    public void runExclusive(String jobKey, Runnable action) {
        boolean executed = lock.tryExecuteWithLock(KEY_PREFIX + jobKey, Duration.ZERO, action);
        if (!executed) {
            log.info("job.skip.concurrent jobKey={} reason=lock-held-by-another-instance", jobKey);
        }
    }
}
