package com.wensaas.core.impl.ticket;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * {@code sys_ticket_ticket} 工单主表实体（租户隔离业务表）。
 *
 * <p>{@code type}/{@code priority}/{@code status} 以 SMALLINT 存，Service 层映射枚举。
 */
@TableName("sys_ticket_ticket")
@Getter
@Setter
public class TicketTicket {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private String ticketNo;
    private String title;
    private Integer type;
    private Integer priority;
    private Integer status;
    private Long submitterId;
    private String submitterName;
    private Long assigneeId;
    private String assigneeName;
    private Instant slaDueAt;
    private Boolean slaWarned;
    private Boolean slaBreached;
    // 重启工单需把这三列清回 null；updateById 默认 NOT_NULL 策略会跳过 null 字段，
    // 故显式 ALWAYS 让 null 也写库（否则 CLOSED/RESOLVED → 重启后残留旧关闭/解决信息）。
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Instant resolvedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Instant closedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String closeReason;
    private Instant lastActivityAt;
    private LocalDateTime createdAt;
    private Long createdBy;
    private Long createdByDept;
    private LocalDateTime updatedAt;
    private Long updatedBy;
    @TableLogic(value = "null", delval = "now()")
    private LocalDateTime deletedAt;

}
