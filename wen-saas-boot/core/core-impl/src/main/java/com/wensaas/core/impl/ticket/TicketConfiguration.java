package com.wensaas.core.impl.ticket;

import com.wensaas.core.api.ops.access.OpsAccessContextResolver;
import com.wensaas.core.api.ticket.OpsTicketService;
import com.wensaas.core.api.ticket.TicketService;
import com.wensaas.core.api.tenant.TenantQueryService;
import com.wensaas.core.api.auth.user.OpsAccountQuery;
import com.wensaas.core.impl.permission.eval.PermissionResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 工单模块 Bean 装配。
 *
 * <p>{@code TicketNoGenerator} / {@code TicketSupport} / 描述符 / Job 为 {@code @Component}，
 * 此处仅装配两个 Service 入口。
 */
@Configuration(proxyBeanMethods = false)
public class TicketConfiguration {

    @Bean
    public TicketService ticketService(TicketTicketMapper ticketMapper,
                                       TicketMessageMapper messageMapper,
                                       TicketNoGenerator noGenerator,
                                       TicketSupport support,
                                       TenantQueryService tenantQuery,
                                       PermissionResolver permissionResolver) {
        return new TicketServiceImpl(ticketMapper, messageMapper, noGenerator, support,
                tenantQuery, permissionResolver);
    }

    @Bean
    public OpsTicketService opsTicketService(TicketTicketMapper ticketMapper,
                                             TicketMessageMapper messageMapper,
                                             TicketSupport support,
                                             TenantQueryService tenantQuery,
                                             OpsAccountQuery opsAccounts,
                                             OpsAccessContextResolver accessResolver) {
        return new OpsTicketServiceImpl(ticketMapper, messageMapper, support, tenantQuery,
                opsAccounts, accessResolver);
    }
}
