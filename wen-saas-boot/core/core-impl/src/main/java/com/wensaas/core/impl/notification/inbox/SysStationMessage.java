package com.wensaas.core.impl.notification.inbox;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * {@code sys_station_message} 实体。按收件人独立行，{@code read_at} 标已读。
 */
@TableName("sys_station_message")
@Getter
@Setter
public class SysStationMessage {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private Long recipientId;
    private String taskId;
    private String category;
    private String sourceModule;
    private String templateCode;
    private String title;
    private String body;
    private String link;
    private String relatedType;
    private String relatedId;
    private String priority;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;

}
