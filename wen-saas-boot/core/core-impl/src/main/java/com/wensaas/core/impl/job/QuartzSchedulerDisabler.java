package com.wensaas.core.impl.job;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.quartz.autoconfigure.SchedulerFactoryBeanCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@code wen-saas-boot.jobs.enabled=false} 的节点：Quartz Scheduler 不自启。
 *
 * <p>JDBC 集群存储下，任何启动着的 Scheduler 都会从 {@code quartz} schema 认领
 * 触发并执行——只拦本地注册不够，必须让关闭调度的节点（含测试上下文）整个退出
 * 集群，否则它会认领任务却因 Job Bean 未装配而执行失败。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "wen-saas-boot.jobs", name = "enabled", havingValue = "false")
public class QuartzSchedulerDisabler {

    @Bean
    public SchedulerFactoryBeanCustomizer jobsDisabledSchedulerCustomizer() {
        return factory -> factory.setAutoStartup(false);
    }
}
