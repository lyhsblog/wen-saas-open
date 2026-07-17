package com.wensaas.core.impl.ticket.spi;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.spi.DashboardCardProvider;
import com.wensaas.core.api.spi.def.DashboardCardData;
import com.wensaas.core.api.ticket.TicketStatus;
import com.wensaas.core.impl.ticket.TicketTicket;
import com.wensaas.core.impl.ticket.TicketTicketMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

/**
 * 工单能力的工作台卡片数据提供者（{@code base.ticket.my_status}「我的工单」，
 * 卡片由 {@code BaseBusinessModuleDescriptor} 声明）。
 *
 * <p>统计当前登录成员（submitter=self）名下工单的 待处理 / 处理中 / 本月解决 计数，
 * 「工单状态分布」卡片。租户线由拦截器自动注入，无需跨租户。
 */
@Component
public class TicketDashboardCardProvider implements DashboardCardProvider {

    private static final ZoneId TZ = ZoneId.of("Asia/Shanghai");

    private final TicketTicketMapper ticketMapper;

    public TicketDashboardCardProvider(TicketTicketMapper ticketMapper) {
        this.ticketMapper = ticketMapper;
    }

    @Override
    public DashboardCardData render() {
        long userId = UserContext.require().userId();
        long pending = countMine(userId, w -> w.eq(TicketTicket::getStatus, TicketStatus.PENDING.dbValue()));
        long processing = countMine(userId, w -> w.eq(TicketTicket::getStatus, TicketStatus.PROCESSING.dbValue()));

        Instant startOfMonth = YearMonth.now(TZ).atDay(1).atStartOfDay(TZ).toInstant();
        long resolvedThisMonth = countMine(userId, w -> w
                .in(TicketTicket::getStatus, TicketStatus.RESOLVED.dbValue(), TicketStatus.CLOSED.dbValue())
                .ge(TicketTicket::getResolvedAt, startOfMonth));

        return DashboardCardData.list(List.of(
                new DashboardCardData.Item("待处理", String.valueOf(pending), pending > 0 ? "danger" : "muted"),
                new DashboardCardData.Item("处理中", String.valueOf(processing), processing > 0 ? "warning" : "muted"),
                new DashboardCardData.Item("本月解决", String.valueOf(resolvedThisMonth), "success")));
    }

    private long countMine(long userId, java.util.function.Consumer<LambdaQueryWrapper<TicketTicket>> extra) {
        LambdaQueryWrapper<TicketTicket> w = new LambdaQueryWrapper<TicketTicket>()
                .eq(TicketTicket::getSubmitterId, userId);
        extra.accept(w);
        return ticketMapper.selectCount(w);
    }
}
