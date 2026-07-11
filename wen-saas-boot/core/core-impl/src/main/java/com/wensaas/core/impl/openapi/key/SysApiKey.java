package com.wensaas.core.impl.openapi.key;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wensaas.core.impl.tenant.PgJsonbTypeHandler;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * {@code sys_api_key} 表实体（混合表，非分区）。
 *
 * <p>{@code secretEncrypted} 走 AES-256-GCM 加密（复用 {@code CredentialCipher}）；
 * {@code ipWhitelist} 走 jsonb，字符串列表形如 {@code ["1.2.3.4","10.0.0.0/8"]}。
 * 不软删（撤销走 status='disabled'）。
 */
@TableName(value = "sys_api_key", autoResultMap = true)
@Getter
@Setter
public class SysApiKey {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private String name;
    private String apiKey;
    private String secretEncrypted;
    @TableField(typeHandler = PgJsonbTypeHandler.class)
    private List<String> ipWhitelist;
    private Boolean requireSignature;
    private LocalDateTime expiresAt;
    private String status;
    /** 凭证级限流配额（每窗口允许请求数）；null = 仅受租户级限流。 */
    private Integer rateLimitQps;
    private Long creatorId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastUsedAt;

}
