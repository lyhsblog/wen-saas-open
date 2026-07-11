package com.wensaas.core.impl.ticket;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** {@code sys_ticket_ticket} Mapper。租户隔离表；运营跨租户查询用 @CrossTenantQuery。 */
@Mapper
public interface TicketTicketMapper extends BaseMapper<TicketTicket> {
}
