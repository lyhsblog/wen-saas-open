package com.wensaas.core.impl.file.storage;

import com.baomidou.mybatisplus.core.plugins.IgnoreStrategy;
import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;
import com.wensaas.core.api.file.FileStorage;
import com.wensaas.core.impl.file.CredentialCipher;

/**
 * 按租户解析 active 存储后端。
 *
 * <p>查找顺序：
 * <ol>
 *   <li>{@code sys_storage_config} WHERE tenant_id=X AND status='active'（MP TenantLine 已注入）</li>
 *   <li>找不到 → 平台默认（{@code wen-saas-boot.file.platform-default-backend}）</li>
 * </ol>
 *
 * <p>解析结果不缓存（Factory 已缓存 OssClient）；查询走 MP TenantLine 自动隔离。
 *
 * <p>注：跨租户查询历史 archived 配置由本类内部 {@link InterceptorIgnoreHelper} 处理，
 * 调用方无需关心。运营场景批量清理（{@code SoftDeletedFileCleanerJob}）也走本类。
 */
public class StorageBackendResolver {

    private static final IgnoreStrategy IGNORE_TENANT_LINE =
            IgnoreStrategy.builder().tenantLine(true).build();

    private final SysStorageConfigMapper configMapper;
    private final CredentialCipher cipher;
    private final FileStorageFactory factory;

    public StorageBackendResolver(SysStorageConfigMapper configMapper,
                                  CredentialCipher cipher,
                                  FileStorageFactory factory) {
        this.configMapper = configMapper;
        this.cipher = cipher;
        this.factory = factory;
    }

    /**
     * 解析当前租户的 active 后端。
     *
     * @return 解析结果，包含 FileStorage 实例 + 解密后的 SysStorageConfig（null 表示走平台默认）。
     */
    public ResolveResult resolveActive(Long tenantId) {
        SysStorageConfig active = configMapper.selectActive(tenantId);
        if (active == null) {
            return new ResolveResult(factory.platformDefault(), null);
        }
        decryptInPlace(active);
        return new ResolveResult(factory.forConfig(active), active);
    }

    /** 按 backend_config_id 拿 FileStorage；用于下载历史文件（可能 archived）。 */
    public FileStorage resolveByConfigId(Long backendConfigId) {
        if (backendConfigId == null || backendConfigId == FileStorageFactory.PLATFORM_DEFAULT_CONFIG_ID) {
            return factory.platformDefault();
        }
        // 历史文件可能在 archived 配置，且跨租户查询（运营场景）也允许；这里强制忽略 TenantLine
        InterceptorIgnoreHelper.handle(IGNORE_TENANT_LINE);
        try {
            SysStorageConfig cfg = configMapper.selectByIdAnyStatus(backendConfigId);
            if (cfg == null) {
                // 配置已被物理删除 —— 极少见，按平台默认兜底
                return factory.platformDefault();
            }
            decryptInPlace(cfg);
            return factory.forConfig(cfg);
        } finally {
            InterceptorIgnoreHelper.clearIgnoreStrategy();
        }
    }

    private void decryptInPlace(SysStorageConfig cfg) {
        if (cfg.getAccessKeySecret() != null) {
            cfg.setAccessKeySecret(cipher.decrypt(cfg.getAccessKeySecret()));
        }
    }

    public record ResolveResult(FileStorage storage, SysStorageConfig config) {
        public Long backendConfigId() {
            return config == null ? FileStorageFactory.PLATFORM_DEFAULT_CONFIG_ID : config.getId();
        }
        public String pathPrefix() {
            return config == null ? null : config.getPathPrefix();
        }
    }
}
