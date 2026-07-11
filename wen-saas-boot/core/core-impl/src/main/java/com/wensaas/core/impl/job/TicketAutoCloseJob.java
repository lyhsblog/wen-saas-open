package com.wensaas.core.impl.job;

import com.wensaas.core.api.spi.ScheduledJob;
import com.wensaas.core.api.ticket.OpsTicketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * 工单自动关闭：已解决满 7 天未回应的工单自动关闭并通知提交人。
 *
 * <p>默认 cron：{@code 0 10 0 * * ?}（每天凌晨）。
 */
@Component
@ConditionalOnProperty(prefix = "wen-saas-boot.jobs", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class TicketAutoCloseJob implements ScheduledJob {

    private static final Logger log = LoggerFactory.getLogger(TicketAutoCloseJob.class);

    private final OpsTicketService opsTicketService;

    public TicketAutoCloseJob(OpsTicketService opsTicketService) {
        this.opsTicketService = opsTicketService;
    }

    @Override
    public String jobKey() {
        return "ticket-auto-close";
    }

    @Override
    public String cron() {
        return "0 10 0 * * ?";
    }

    /** 执行体（锁由调度框架统一加）；单测可直接调用。 */
    @Override
    public void execute() {
        int closed = opsTicketService.runAutoClose(Instant.now());
        log.info("[TicketAutoCloseJob] 自动关闭已解决超 7 天工单 {} 条", closed);
    }
}
