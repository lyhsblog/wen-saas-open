package com.wensaas.core.impl.ticket;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** {@code sys_ticket_attachment} 附件实体（关联底座 sys_file，business-only）。 */
@TableName("sys_ticket_attachment")
@Getter
@Setter
public class TicketAttachment {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private Long ticketId;
    private Long messageId;
    private String fileId;
    private String fileName;
    private Long sizeBytes;
    private LocalDateTime createdAt;
    private Long createdBy;

}
