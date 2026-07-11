package com.wensaas.core.impl.job;

import com.wensaas.core.api.spi.ScheduledJob;
import com.wensaas.core.impl.tenant.TenantLifecycleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 每日 00:05 扫描 {@code expire_at < now() AND status != 2} 的租户，
 * 批量置 status=2（已过期）并广播本地缓存失效。
 *
 * <p>默认 cron：{@code 0 5 0 * * ?}。一次处理 {@link #BATCH_SIZE} 条。
 * 实际「写库 + 失效」由 tenant domain 的 {@link TenantLifecycleService} 收口，
 * 本类只负责调度执行体。
 */
@Component
@ConditionalOnProperty(prefix = "wen-saas-boot.jobs", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class TenantExpireJob implements ScheduledJob {

    private static final Logger log = LoggerFactory.getLogger(TenantExpireJob.class);
    private static final int BATCH_SIZE = 200;

    private final TenantLifecycleService tenantLifecycleService;

    public TenantExpireJob(TenantLifecycleService tenantLifecycleService) {
        this.tenantLifecycleService = tenantLifecycleService;
    }

    @Override
    public String jobKey() {
        return "tenant-expire";
    }

    @Override
    public String cron() {
        return "0 5 0 * * ?";
    }

    /** 执行体（锁由调度框架统一加）；单测可直接调用。 */
    @Override
    public void execute() {
        int count = tenantLifecycleService.markExpiredTenants(BATCH_SIZE);
        if (count == 0) {
            log.info("[TenantExpireJob] 无需处理");
        } else {
            log.info("[TenantExpireJob] 置过期租户 {} 条", count);
        }
    }
}
