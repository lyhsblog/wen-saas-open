package com.wensaas.core.impl.file.storage;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import com.wensaas.core.impl.file.CredentialCipher;

/**
 * {@code sys_storage_config} 表实体（混合表）。
 *
 * <p>每租户至多一个 {@code status='active'} 配置（DB 层 partial unique index 兜底）；
 * 切换后旧配置标 {@code archived}，历史文件仍可按其 backend_config_id 找到凭证下载。
 *
 * <p>{@link #accessKeySecret} 列存的是 AES-256-GCM 加密后的 Base64（含 IV）。
 * 实体本身不做透明加解密；由 {@code StorageConfigManager} 在 read / write 边界手工调
 * {@link CredentialCipher}。
 */
@TableName("sys_storage_config")
@Getter
@Setter
public class SysStorageConfig {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private String backendType;
    private String endpoint;
    private String bucket;
    private String accessKeyId;
    private String accessKeySecret;
    private String pathPrefix;
    private String status;
    private LocalDateTime createdAt;
    private Long createdBy;
    private LocalDateTime updatedAt;
    private Long updatedBy;

}
