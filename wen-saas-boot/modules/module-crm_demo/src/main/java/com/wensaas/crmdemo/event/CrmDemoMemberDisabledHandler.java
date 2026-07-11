package com.wensaas.crmdemo.event;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.wensaas.core.api.event.CoreEventTypes;
import com.wensaas.core.api.event.DomainEventHandler;
import com.wensaas.core.api.event.EventEnvelope;
import com.wensaas.core.api.notification.send.Channel;
import com.wensaas.core.api.notification.send.MessageSendRequest;
import com.wensaas.core.api.notification.send.MessageService;
import com.wensaas.core.api.notification.send.Priority;
import com.wensaas.core.api.notification.send.RecipientSpec;
import com.wensaas.core.api.tenant.TenantQueryService;
import com.wensaas.crmdemo.entity.CrmDemoCustomer;
import com.wensaas.crmdemo.repository.CrmDemoCustomerMapper;
import com.wensaas.crmdemo.spi.CrmDemoModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 订阅平台「成员停用」事件（{@code sys.member_disabled}）：统计该成员名下未终态客户，
 * 站内信提醒主管理员与相关归属部门的负责人改派。{@code eventSubscriptions()} SPI 的首个业务订阅示例。
 *
 * <p>Bean 名 {@link #BEAN_NAME} 与 {@code CrmDemoModule.eventSubscriptions()} 声明一致（启动期校验）。
 * 调用发生在事件租户上下文内；发站内信本身异步且按 eventId 有幂等守卫，实现无额外状态可安全重做。
 */
@Component(CrmDemoMemberDisabledHandler.BEAN_NAME)
public class CrmDemoMemberDisabledHandler implements DomainEventHandler {

    public static final String BEAN_NAME = "crmDemoMemberDisabledHandler";

    private static final Logger log = LoggerFactory.getLogger(CrmDemoMemberDisabledHandler.class);

    private final CrmDemoCustomerMapper customerMapper;
    private final MessageService messageService;
    private final TenantQueryService tenantQuery;

    public CrmDemoMemberDisabledHandler(CrmDemoCustomerMapper customerMapper,
                                        MessageService messageService,
                                        TenantQueryService tenantQuery) {
        this.customerMapper = customerMapper;
        this.messageService = messageService;
        this.tenantQuery = tenantQuery;
    }

    @Override
    public void onEvent(EventEnvelope event) {
        CoreEventTypes.MemberDisabledPayload payload = JSON.to(
                CoreEventTypes.MemberDisabledPayload.class, event.payload());
        long ownerId = Long.parseLong(payload.userId());

        // 只统计还需跟进的客户（终态 WON/LOST 无需改派）
        Long count = customerMapper.selectCount(new QueryWrapper<CrmDemoCustomer>()
                .eq("owner_id", ownerId)
                .in("status", "LEAD", "FOLLOWING"));
        if (count == null || count == 0) {
            return;
        }

        List<RecipientSpec> recipients = new ArrayList<>();
        TenantQueryService.TenantInfo tenant = tenantQuery.tenantById(event.tenantId());
        if (tenant != null && tenant.mainAdminUserId() != null) {
            recipients.add(RecipientSpec.member(tenant.mainAdminUserId()));
        }
        // 受影响客户的归属部门负责人（去重后逐部门 deptLeader）
        List<Object> deptIds = customerMapper.selectObjs(new QueryWrapper<CrmDemoCustomer>()
                .select("DISTINCT owner_dept_id")
                .eq("owner_id", ownerId)
                .in("status", "LEAD", "FOLLOWING")
                .isNotNull("owner_dept_id"));
        for (Object deptId : deptIds) {
            recipients.add(RecipientSpec.deptLeader(((Number) deptId).longValue()));
        }
        if (recipients.isEmpty()) {
            log.info("crm_demo.member-disabled.no-recipient tenantId={} ownerId={}", event.tenantId(), ownerId);
            return;
        }

        messageService.send(MessageSendRequest.builder(CrmDemoModule.TPL_OWNER_DISABLED)
                .variables(Map.of(
                        "memberName", payload.realName() == null ? String.valueOf(ownerId) : payload.realName(),
                        "count", count))
                .recipients(recipients)
                .channels(List.of(Channel.STATION))
                .priority(Priority.IMPORTANT)
                .link("/crm-demo/customers")
                .build());
        log.info("crm_demo.member-disabled.reminder-sent tenantId={} ownerId={} customers={}",
                event.tenantId(), ownerId, count);
    }
}
