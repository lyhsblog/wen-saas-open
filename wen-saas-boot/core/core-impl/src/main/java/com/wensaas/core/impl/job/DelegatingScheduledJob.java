package com.wensaas.core.impl.job;

import com.wensaas.core.api.spi.ScheduledJob;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Quartz 触发 → {@link ScheduledJob} 的统一委派入口。
 *
 * <p>JDBC 集群存储只持久化 JobKey（即 {@code ScheduledJob.jobKey()}），执行体经
 * {@link ScheduledJobRegistry} 在触发时找回；字段注入由 Spring Boot 的
 * {@code AutowireCapableBeanJobFactory} 在每次实例化时完成。库里存在而代码已删除的
 * 孤儿注册项（注册器启动清理前的窗口内触发）记 warn 跳过。
 *
 * <p>执行体统一套 {@link JobLockSupport#runExclusive} Redis 锁——集群模式下 Quartz
 * 已保证单节点触发，这道锁是第二道兜底（防误配成多套调度、或改回内存存储的部署形态）。
 * 异常在此兜底记日志，不向 Quartz 上抛（避免误触发 refire 语义）。
 *
 * <p>{@code @DisallowConcurrentExecution}：同一 JobKey 上次未跑完时集群内不并发再跑。
 */
@DisallowConcurrentExecution
public class DelegatingScheduledJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(DelegatingScheduledJob.class);

    @Autowired
    private ScheduledJobRegistry registry;
    @Autowired
    private JobLockSupport jobLock;

    @Override
    public void execute(JobExecutionContext context) {
        String jobKey = context.getJobDetail().getKey().getName();
        ScheduledJob job = registry.find(jobKey);
        if (job == null) {
            log.warn("job.run.orphan jobKey={} 代码中已无此任务，等待注册器清理", jobKey);
            return;
        }
        try {
            jobLock.runExclusive(jobKey, job::execute);
        } catch (RuntimeException ex) {
            log.error("job.run.failed jobKey={}", jobKey, ex);
        }
    }
}
