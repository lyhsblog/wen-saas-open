package com.wensaas.core.impl.job;

import com.wensaas.core.api.auth.user.OpsAccountQuery;
import com.wensaas.core.api.notification.send.Channel;
import com.wensaas.core.api.notification.send.MessageCategory;
import com.wensaas.core.api.notification.send.MessageSendRequest;
import com.wensaas.core.api.notification.send.MessageService;
import com.wensaas.core.api.notification.send.Priority;
import com.wensaas.core.api.notification.send.RecipientSpec;
import com.wensaas.core.api.ops.health.OpsHealthService;
import com.wensaas.core.api.ops.health.OpsHealthService.DangerBrief;
import com.wensaas.core.api.ops.health.OpsHealthService.RiskSignal;
import com.wensaas.core.api.spi.ScheduledJob;
import com.wensaas.core.api.tenant.CrossTenantQuery;
import com.wensaas.core.api.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 客户健康度每日计算。
 *
 * <p>每天凌晨为全部租户重算评分落 {@code sys_tenant_health}；随后把危险档租户清单（最多 20 条）
 * 经站内信推送给所有运营人员。
 *
 * <p>默认 cron：{@code 0 30 0 * * ?}（在 TenantExpireJob 之后）。
 */
@Component
@ConditionalOnProperty(prefix = "wen-saas-boot.jobs", name = "enabled",
        havingValue = "true", matchIfMissing = true)
@CrossTenantQuery("TenantHealthScoreJob: 运营调度，全量重算无租户上下文")
public class TenantHealthScoreJob implements ScheduledJob {

    private static final Logger log = LoggerFactory.getLogger(TenantHealthScoreJob.class);
    private static final int DIGEST_LIMIT = 20;

    private final OpsHealthService healthService;
    private final OpsAccountQuery opsAccounts;
    private final ObjectProvider<MessageService> messageServiceProvider;

    public TenantHealthScoreJob(OpsHealthService healthService,
                                OpsAccountQuery opsAccounts,
                                ObjectProvider<MessageService> messageServiceProvider) {
        this.healthService = healthService;
        this.opsAccounts = opsAccounts;
        this.messageServiceProvider = messageServiceProvider;
    }

    @Override
    public String jobKey() {
        return "tenant-health-score";
    }

    @Override
    public String cron() {
        return "0 30 0 * * ?";
    }

    /** 执行体（锁由调度框架统一加）；单测可直接调用。 */
    @Override
    public void execute() {
        OpsHealthService.RecomputeResult result = healthService.recomputeAll();
        log.info("[TenantHealthScoreJob] 重算 {} 个租户：健康 {} / 关注 {} / 危险 {}",
                result.total(), result.healthy(), result.warning(), result.danger());
        if (!result.dangerList().isEmpty()) {
            pushDangerDigest(result.dangerList());
        }
    }

    /** 把危险租户清单推送给所有运营人员（站内信）。失败不影响重算主流程。 */
    private void pushDangerDigest(List<DangerBrief> dangerList) {
        MessageService messageService = messageServiceProvider.getIfAvailable();
        if (messageService == null) {
            return;
        }
        try {
            TenantContext.callAs(TenantContext.PLATFORM_TENANT_ID, () -> {
                List<RecipientSpec> recipients = opsAccounts.activeOpsUserIds().stream()
                        .map(RecipientSpec::member).toList();
                if (recipients.isEmpty()) {
                    return null;
                }
                int total = dangerList.size();
                List<DangerBrief> shown = dangerList.size() > DIGEST_LIMIT
                        ? dangerList.subList(0, DIGEST_LIMIT) : dangerList;
                StringBuilder sb = new StringBuilder();
                for (DangerBrief d : shown) {
                    sb.append("· ").append(d.tenantName()).append("：")
                            .append(d.riskSignals().stream().map(RiskSignal::label)
                                    .collect(Collectors.joining("、")))
                            .append('\n');
                }
                if (total > DIGEST_LIMIT) {
                    sb.append("还有 ").append(total - DIGEST_LIMIT).append(" 个危险租户");
                }
                messageService.send(MessageSendRequest.builder("sys.health.danger_digest")
                        .variables(Map.of("danger_count", total, "danger_list", sb.toString().trim()))
                        .recipients(recipients)
                        .channels(List.of(Channel.STATION))
                        .category(MessageCategory.SYSTEM)
                        .priority(Priority.IMPORTANT)
                        .build());
                return null;
            });
        } catch (Exception e) {
            log.warn("[TenantHealthScoreJob] 危险租户摘要推送失败：{}", e.getMessage());
        }
    }
}
