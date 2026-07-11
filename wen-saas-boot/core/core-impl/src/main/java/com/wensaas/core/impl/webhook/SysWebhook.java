package com.wensaas.core.impl.webhook;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wensaas.core.impl.tenant.PgJsonbTypeHandler;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/**
 * {@code sys_webhook} 表实体（混合表，非分区）。
 *
 * <p>{@code signingSecretEncrypted} 走 AES-256-GCM（{@code CredentialCipher}）；
 * {@code subscribedEvents} 走 jsonb 字符串列表；{@code retryPolicy} 走 jsonb 可空（NULL 取全局默认）。
 */
@TableName(value = "sys_webhook", autoResultMap = true)
@Getter
@Setter
public class SysWebhook {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private String name;
    private String targetUrl;
    private String signingSecretEncrypted;
    @TableField(typeHandler = PgJsonbTypeHandler.class)
    private List<String> subscribedEvents;
    @TableField(typeHandler = PgJsonbTypeHandler.class)
    private Map<String, Object> retryPolicy;
    private Integer consecutiveFailures;
    private String status;
    private Long creatorId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
