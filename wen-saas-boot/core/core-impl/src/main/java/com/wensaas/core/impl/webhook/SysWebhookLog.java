package com.wensaas.core.impl.webhook;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * {@code sys_webhook_log} 表实体（混合表 + 月度分区）。
 * 不软删；不用 @TableLogic；{@code createdAt} 必须应用层赋值。
 */
@TableName(value = "sys_webhook_log", autoResultMap = true)
@Getter
@Setter
public class SysWebhookLog {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private Long webhookId;
    private String eventType;
    private String eventId;
    private String requestBody;
    private String requestId;
    private Integer httpStatus;
    private String responseBody;
    private Integer durationMs;
    private String errorMessage;
    private Integer retryCount;
    private String finalStatus;
    private LocalDateTime createdAt;

}
