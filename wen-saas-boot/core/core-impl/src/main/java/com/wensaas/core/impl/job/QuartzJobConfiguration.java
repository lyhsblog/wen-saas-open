package com.wensaas.core.impl.job;

import com.wensaas.core.api.spi.ScheduledJob;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.impl.matchers.GroupMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashSet;
import java.util.Set;

/**
 * 定时任务调度装配：把容器内全部 {@link ScheduledJob} Bean 注册进 Quartz
 * （JDBC 集群存储，表在 PG 独立 {@code quartz} schema，集群内单节点触发 + misfire 补跑）。
 *
 * <p>注册是 upsert 语义（{@code replace=true}）：重启 / 改 cron 都以代码与配置的
 * 当前值覆盖库中注册；代码里已删除的 jobKey 同步摘除库中孤儿注册项，否则集群
 * 会继续调度一个不存在的 Job。cron 取 {@code wen-saas-boot.jobs.cron.<jobKey>}
 * 覆盖值，缺省用 {@link ScheduledJob#cron()}；jobKey 重复或 cron 非法启动期 fail-fast。
 *
 * <p>{@code wen-saas-boot.jobs.enabled=false}（默认 true）时本装配与各 Job Bean
 * （同门禁）整体不生效，且该节点的 Quartz Scheduler 不启动、不参与集群认领
 * （见 {@link QuartzSchedulerDisabler}）。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JobProperties.class)
@ConditionalOnProperty(prefix = "wen-saas-boot.jobs", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class QuartzJobConfiguration {

    private static final Logger log = LoggerFactory.getLogger(QuartzJobConfiguration.class);

    @Bean
    public ScheduledJobRegistry scheduledJobRegistry(ObjectProvider<ScheduledJob> jobProvider) {
        return new ScheduledJobRegistry(jobProvider.orderedStream().toList());
    }

    @Bean
    public SmartInitializingSingleton scheduledJobRegistrar(Scheduler scheduler,
                                                            ScheduledJobRegistry registry,
                                                            JobProperties props) {
        return () -> {
            Set<JobKey> desired = new HashSet<>();
            for (ScheduledJob job : registry.all()) {
                String cron = props.getCron().getOrDefault(job.jobKey(), job.cron());
                desired.add(register(scheduler, job, cron));
            }
            removeOrphans(scheduler, desired);
            log.info("[Jobs] 已注册定时任务 {} 个: {}", desired.size(),
                    registry.all().stream().map(ScheduledJob::jobKey).toList());
        };
    }

    private JobKey register(Scheduler scheduler, ScheduledJob job, String cron) {
        JobDetail detail = JobBuilder.newJob(DelegatingScheduledJob.class)
                .withIdentity(job.jobKey())
                .build();
        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(job.jobKey())
                .withSchedule(CronScheduleBuilder.cronSchedule(cron)
                        // 停机漏跑（misfire）恢复后补一次，然后回归正常节奏
                        .withMisfireHandlingInstructionFireAndProceed())
                .build();
        try {
            scheduler.scheduleJob(detail, Set.of(trigger), true);
        } catch (SchedulerException e) {
            throw new IllegalStateException("注册定时任务失败: " + job.jobKey(), e);
        }
        log.info("[Jobs] {} cron={}", job.jobKey(), cron);
        return detail.getKey();
    }

    /** 摘除库里存在、代码里已删除的注册项。 */
    private void removeOrphans(Scheduler scheduler, Set<JobKey> desired) {
        try {
            for (JobKey existing : scheduler.getJobKeys(GroupMatcher.anyJobGroup())) {
                if (!desired.contains(existing)) {
                    scheduler.deleteJob(existing);
                    log.warn("[Jobs] 摘除孤儿注册项 {}（代码中已无此任务）", existing);
                }
            }
        } catch (SchedulerException e) {
            throw new IllegalStateException("清理孤儿定时任务失败", e);
        }
    }
}
