package com.wensaas.core.impl.file;

import com.wensaas.core.api.audit.AuditLogRecorder;
import com.wensaas.core.api.file.FilePermissionChecker;
import com.wensaas.core.api.file.FileService;
import com.wensaas.core.api.file.center.FileCenterService;
import com.wensaas.core.api.file.share.FileShareService;
import com.wensaas.core.impl.auth.password.PasswordHasher;
import com.wensaas.core.api.tenant.TenantQueryService;
import com.wensaas.core.impl.file.share.FileShareServiceImpl;
import com.wensaas.core.impl.file.share.SysFileShareMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import com.wensaas.core.impl.file.storage.FileStorageFactory;
import com.wensaas.core.impl.file.storage.StorageBackendResolver;
import com.wensaas.core.impl.file.storage.StorageConfigManager;
import com.wensaas.core.impl.file.storage.SysStorageConfigMapper;

/**
 * 文件存储框架装配。{@code wen-saas-boot.file.enabled=true}（默认）时启用。
 * 关闭时由 {@link NoopFileConfiguration} 兜底注入 Noop {@link FileService}。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(FileProperties.class)
@ConditionalOnProperty(prefix = "wen-saas-boot.file", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class FileConfiguration {

    private static final Logger log = LoggerFactory.getLogger(FileConfiguration.class);

    @Bean
    public CredentialCipher fileCredentialCipher(FileProperties props) {
        return new CredentialCipher(props);
    }

    @Bean(destroyMethod = "shutdownAll")
    public FileStorageFactory fileStorageFactory(FileProperties props, CredentialCipher cipher) {
        log.info("[FileStorage] enabled, platform default backend = {}, local root = {}",
                props.getPlatformDefaultBackend().code(),
                props.getLocal().getRoot());
        return new FileStorageFactory(props, cipher);
    }

    @Bean
    public StorageBackendResolver storageBackendResolver(SysStorageConfigMapper mapper,
                                                          CredentialCipher cipher,
                                                          FileStorageFactory factory) {
        return new StorageBackendResolver(mapper, cipher, factory);
    }

    @Bean
    public FileDownloadUrlCache fileDownloadUrlCache(StringRedisTemplate redis, FileProperties props) {
        return new FileDownloadUrlCache(redis, props);
    }

    @Bean
    public FilePermissionEvaluator filePermissionEvaluator(
            @Autowired(required = false) List<FilePermissionChecker> checkers) {
        return new FilePermissionEvaluator(checkers == null ? List.of() : checkers);
    }

    @Bean
    public StorageConfigManager storageConfigManager(SysStorageConfigMapper mapper,
                                                      CredentialCipher cipher,
                                                      FileStorageFactory factory,
                                                      AuditLogRecorder auditRecorder) {
        return new StorageConfigManager(mapper, cipher, factory, auditRecorder);
    }

    @Bean
    @ConditionalOnMissingBean(FileService.class)
    public FileService fileService(SysFileMapper fileMapper,
                                    StorageBackendResolver resolver,
                                    FileDownloadUrlCache urlCache,
                                    FilePermissionEvaluator permissionEvaluator,
                                    FileProperties props) {
        return new FileServiceImpl(fileMapper, resolver, urlCache, permissionEvaluator, props);
    }

    @Bean
    @ConditionalOnMissingBean(FileCenterService.class)
    public FileCenterService fileCenterService(SysFileMapper fileMapper,
                                               SysFileFolderMapper folderMapper,
                                               TenantQueryService tenantQuery,
                                               FileService fileService,
                                               StorageBackendResolver resolver,
                                               FileDownloadUrlCache urlCache,
                                               FileProperties props) {
        return new FileCenterServiceImpl(fileMapper, folderMapper, tenantQuery, fileService,
                resolver, urlCache, props);
    }

    @Bean
    @ConditionalOnMissingBean(FileShareService.class)
    public FileShareService fileShareService(SysFileShareMapper mapper,
                                              FileService fileService,
                                              PasswordHasher passwordHasher,
                                              FileProperties props) {
        return new FileShareServiceImpl(mapper, fileService, passwordHasher, props);
    }
}
