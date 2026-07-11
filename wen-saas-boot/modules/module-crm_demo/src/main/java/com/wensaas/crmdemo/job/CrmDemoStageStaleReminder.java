package com.wensaas.crmdemo.job;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.wensaas.core.api.notification.send.MessageSendRequest;
import com.wensaas.core.api.notification.send.MessageService;
import com.wensaas.core.api.notification.send.RecipientSpec;
import com.wensaas.core.api.tenant.TenantContext;
import com.wensaas.core.api.tenant.TenantQueryService;
import com.wensaas.crmdemo.entity.CrmDemoCustomer;
import com.wensaas.crmdemo.repository.CrmDemoCustomerMapper;
import com.wensaas.crmdemo.spi.CrmDemoModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 「阶段停留超期提醒」扫描逻辑（与调度解耦，可直接调用做验证）。
 *
 * <p>逐正常租户遍历（{@link TenantQueryService#activeTenantIds()} + {@code TenantContext.runAs}，
 * 单租户内 Mapper 走 TenantLine 自动隔离），找出 LEAD / FOLLOWING 且 {@code stage_changed_at}
 * 早于阈值（{@link CrmDemoProperties#getStageStaleDays()} 天前）的客户，按负责人聚合后每人一条
 * 站内信（模板 {@code crm_demo.customer_stage_stale}），无负责人的客户跳过。
 *
 * <p>调度入口在 {@link CrmDemoStageStaleJob}；本组件始终装配，
 * 关闭调度的环境可注入后直接调 {@link #remindAll()}。
 */
@Component
@EnableConfigurationProperties(CrmDemoProperties.class)
public class CrmDemoStageStaleReminder {

    private static final Logger log = LoggerFactory.getLogger(CrmDemoStageStaleReminder.class);

    private final CrmDemoCustomerMapper mapper;
    private final MessageService messageService;
    private final TenantQueryService tenantQuery;
    private final CrmDemoProperties props;

    public CrmDemoStageStaleReminder(CrmDemoCustomerMapper mapper, MessageService messageService,
                                     TenantQueryService tenantQuery, CrmDemoProperties props) {
        this.mapper = mapper;
        this.messageService = messageService;
        this.tenantQuery = tenantQuery;
        this.props = props;
    }

    /** 全租户扫描；返回发出的提醒条数（= 有超期客户的负责人数）。 */
    public int remindAll() {
        int reminders = 0;
        for (Long tenantId : tenantQuery.activeTenantIds()) {
            reminders += TenantContext.callAs(tenantId, this::remindCurrentTenant);
        }
        log.info("crm_demo.stage-stale.done reminders={} thresholdDays={}", reminders, props.getStageStaleDays());
        return reminders;
    }

    /** 当前租户上下文内扫描一轮；返回发出的提醒条数。 */
    public int remindCurrentTenant() {
        LocalDateTime deadline = LocalDateTime.now().minusDays(props.getStageStaleDays());
        List<CrmDemoCustomer> stale = mapper.selectList(new QueryWrapper<CrmDemoCustomer>()
                .select("id", "name", "status", "owner_id")
                .in("status", "LEAD", "FOLLOWING")
                .lt("stage_changed_at", deadline)
                .isNotNull("owner_id"));
        if (stale.isEmpty()) {
            return 0;
        }

        // 按负责人聚合：每人一条汇总提醒，避免逐客户刷屏
        Map<Long, Integer> countByOwner = new LinkedHashMap<>();
        for (CrmDemoCustomer c : stale) {
            countByOwner.merge(c.getOwnerId(), 1, Integer::sum);
        }
        countByOwner.forEach((ownerId, count) -> messageService.send(
                MessageSendRequest.builder(CrmDemoModule.TPL_STAGE_STALE)
                        .variables(Map.of("count", count, "days", props.getStageStaleDays()))
                        .recipients(List.of(RecipientSpec.member(ownerId)))
                        .link("/crm-demo/my-customers")
                        .build()));
        log.info("crm_demo.stage-stale.reminded tenantId={} staleCustomers={} owners={}",
                TenantContext.require(), stale.size(), countByOwner.size());
        return countByOwner.size();
    }
}
