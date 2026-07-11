package com.wensaas.core.impl.ticket;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** {@code sys_ticket_message} 沟通记录实体（含首条提交内容）。 */
@TableName("sys_ticket_message")
@Getter
@Setter
public class TicketMessage {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private Long ticketId;
    private Long authorId;
    private String authorName;
    private Integer authorSide;
    private String content;
    private LocalDateTime createdAt;
    private Long createdBy;

}
