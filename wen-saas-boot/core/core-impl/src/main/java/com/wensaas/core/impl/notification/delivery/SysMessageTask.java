package com.wensaas.core.impl.notification.delivery;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * {@code sys_message_task} 实体。按 收件人 × 通道 一行；不软删。
 */
@TableName("sys_message_task")
@Getter
@Setter
public class SysMessageTask {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private String taskId;
    private String templateCode;
    private Long recipientId;
    private String channel;
    private String priority;
    private String renderedTitle;
    private String renderedBody;
    private String relatedType;
    private String relatedId;
    private String link;
    private String status;
    private Integer retryCount;
    private String errorReason;
    private LocalDateTime sentAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
