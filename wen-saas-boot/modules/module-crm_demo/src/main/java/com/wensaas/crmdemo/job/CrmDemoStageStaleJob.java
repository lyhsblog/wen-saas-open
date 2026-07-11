package com.wensaas.crmdemo.job;

import com.wensaas.core.api.spi.ScheduledJob;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 「阶段停留超期提醒」调度入口——业务模块注册定时任务（{@link ScheduledJob} SPI）的示例。
 *
 * <p>实现接口即挂入底座内嵌 Quartz：默认 cron {@code 0 0 9 * * ?}（每日 09:00，
 * 可用 {@code wen-saas-boot.jobs.cron.crm-demo-stage-stale} 覆盖），多实例单跑锁由
 * 调度框架统一加，实现内不需要自己处理。
 *
 * <p>扫描逻辑在 {@link CrmDemoStageStaleReminder}（始终装配），关闭调度
 * （{@code wen-saas-boot.jobs.enabled=false}）的环境可直接调用验证。
 */
@Component
@ConditionalOnProperty(prefix = "wen-saas-boot.jobs", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class CrmDemoStageStaleJob implements ScheduledJob {

    private final CrmDemoStageStaleReminder reminder;

    public CrmDemoStageStaleJob(CrmDemoStageStaleReminder reminder) {
        this.reminder = reminder;
    }

    @Override
    public String jobKey() {
        return "crm-demo-stage-stale";
    }

    @Override
    public String cron() {
        return "0 0 9 * * ?";
    }

    @Override
    public void execute() {
        reminder.remindAll();
    }
}
