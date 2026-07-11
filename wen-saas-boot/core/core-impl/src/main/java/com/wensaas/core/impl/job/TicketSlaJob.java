package com.wensaas.core.impl.job;

import com.wensaas.core.api.spi.ScheduledJob;
import com.wensaas.core.api.ticket.OpsTicketService;
import com.wensaas.core.api.ticket.OpsTicketService.SlaScanResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * 工单 SLA 扫描：临近(80%) 发提醒标 warned、超时发提醒标 breached。
 *
 * <p>默认 cron：{@code 0 0/10 * * * ?}（每 10 分钟）。
 */
@Component
@ConditionalOnProperty(prefix = "wen-saas-boot.jobs", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class TicketSlaJob implements ScheduledJob {

    private static final Logger log = LoggerFactory.getLogger(TicketSlaJob.class);

    private final OpsTicketService opsTicketService;

    public TicketSlaJob(OpsTicketService opsTicketService) {
        this.opsTicketService = opsTicketService;
    }

    @Override
    public String jobKey() {
        return "ticket-sla-scan";
    }

    @Override
    public String cron() {
        return "0 0/10 * * * ?";
    }

    /** 执行体（锁由调度框架统一加）；单测可直接调用。 */
    @Override
    public void execute() {
        SlaScanResult r = opsTicketService.runSlaScan(Instant.now());
        log.info("[TicketSlaJob] SLA 扫描：临近提醒 {} 条 / 超时提醒 {} 条", r.warned(), r.breached());
    }
}
