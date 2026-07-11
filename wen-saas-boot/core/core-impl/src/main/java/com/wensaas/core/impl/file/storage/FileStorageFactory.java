package com.wensaas.core.impl.file.storage;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.region.Region;
import com.wensaas.core.api.file.FileBackendType;
import com.wensaas.core.api.file.FileException;
import com.wensaas.core.api.file.FileStorage;
import com.wensaas.core.impl.file.adapter.AliyunOssFileStorageAdapter;
import com.wensaas.core.impl.file.adapter.LocalFileStorageAdapter;
import com.wensaas.core.impl.file.adapter.TencentCosFileStorageAdapter;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.wensaas.core.impl.file.CredentialCipher;
import com.wensaas.core.impl.file.FileProperties;

/**
 * {@link FileStorage} 适配器工厂。
 *
 * <p>LocalFileStorageAdapter 单例（无状态）；{@link AliyunOssFileStorageAdapter} /
 * {@link TencentCosFileStorageAdapter} 按 {@code backendConfigId} 缓存，避免每次调用都 new 客户端。
 *
 * <p>{@link #evict(Long)} 在 StorageConfigManager 切换 / 删除配置时调，释放对应客户端。
 */
public class FileStorageFactory {

    private static final Logger log = LoggerFactory.getLogger(FileStorageFactory.class);
    /** sys_storage_config 不存在时（即纯 application.yml 平台配置）使用的占位 id。 */
    public static final long PLATFORM_DEFAULT_CONFIG_ID = 0L;

    private final FileProperties props;
    private final LocalFileStorageAdapter localAdapter;
    /** key = backendConfigId（PLATFORM_DEFAULT_CONFIG_ID 表示 application.yml 平台 OSS）。 */
    private final Map<Long, AliyunOssFileStorageAdapter> aliyunOssCache = new ConcurrentHashMap<>();
    /** key = backendConfigId（PLATFORM_DEFAULT_CONFIG_ID 表示 application.yml 平台 COS）。 */
    private final Map<Long, TencentCosFileStorageAdapter> tencentCosCache = new ConcurrentHashMap<>();

    public FileStorageFactory(FileProperties props, CredentialCipher cipher) {
        this.props = props;
        Path root = Paths.get(props.getLocal().getRoot()).toAbsolutePath();
        // 复用 active key 的 32 字节做 LocalAdapter HMAC key。
        // 不做轮转：HMAC token TTL 5min 自然过期；轮转期间旧 token 失效重试即可。
        String activeKeyId = cipher.activeKeyId();
        String activeKeyBase64 = props.resolveKeys().get(activeKeyId);
        byte[] hmacKey = Base64.getDecoder().decode(activeKeyBase64);
        this.localAdapter = new LocalFileStorageAdapter(root, hmacKey);
    }

    /** 平台默认后端（无 sys_storage_config 时使用）。 */
    public FileStorage platformDefault() {
        FileBackendType type = props.getPlatformDefaultBackend();
        return switch (type) {
            case LOCAL -> localAdapter;
            case ALIYUN_OSS -> aliyunOssCache.computeIfAbsent(PLATFORM_DEFAULT_CONFIG_ID,
                    id -> buildAliyunOssFromProps());
            case TENCENT_COS -> tencentCosCache.computeIfAbsent(PLATFORM_DEFAULT_CONFIG_ID,
                    id -> buildTencentCosFromProps());
        };
    }

    /** 按 sys_storage_config 实例化 / 取缓存。{@code config.accessKeySecret} 必须已解密。 */
    public FileStorage forConfig(SysStorageConfig config) {
        FileBackendType type = FileBackendType.fromCode(config.getBackendType());
        return switch (type) {
            case LOCAL -> localAdapter;
            case ALIYUN_OSS -> aliyunOssCache.computeIfAbsent(config.getId(),
                    id -> buildAliyunOss(config));
            case TENCENT_COS -> tencentCosCache.computeIfAbsent(config.getId(),
                    id -> buildTencentCos(config));
        };
    }

    public LocalFileStorageAdapter localAdapter() {
        return localAdapter;
    }

    /** 主动释放某 backendConfigId 的客户端（切换 / 删除 / archived 时）。 */
    public void evict(Long backendConfigId) {
        AliyunOssFileStorageAdapter oss = aliyunOssCache.remove(backendConfigId);
        if (oss != null) {
            oss.shutdown();
            log.info("[FileStorageFactory] evicted OSS adapter, configId={}", backendConfigId);
        }
        TencentCosFileStorageAdapter cos = tencentCosCache.remove(backendConfigId);
        if (cos != null) {
            cos.shutdown();
            log.info("[FileStorageFactory] evicted COS adapter, configId={}", backendConfigId);
        }
    }

    @PreDestroy
    public void shutdownAll() {
        aliyunOssCache.values().forEach(AliyunOssFileStorageAdapter::shutdown);
        aliyunOssCache.clear();
        tencentCosCache.values().forEach(TencentCosFileStorageAdapter::shutdown);
        tencentCosCache.clear();
    }

    private AliyunOssFileStorageAdapter buildAliyunOss(SysStorageConfig config) {
        if (config.getEndpoint() == null || config.getBucket() == null
                || config.getAccessKeyId() == null || config.getAccessKeySecret() == null) {
            throw new FileException(FileException.Code.INVALID_BACKEND_CONFIG,
                    "OSS 配置字段缺失（endpoint/bucket/accessKeyId/accessKeySecret）");
        }
        OSS client = new OSSClientBuilder().build(
                config.getEndpoint(), config.getAccessKeyId(), config.getAccessKeySecret());
        return new AliyunOssFileStorageAdapter(client, config.getBucket(), config.getEndpoint());
    }

    private AliyunOssFileStorageAdapter buildAliyunOssFromProps() {
        FileProperties.PlatformOss oss = props.getPlatformOss();
        if (oss.getEndpoint() == null || oss.getBucket() == null
                || oss.getAccessKeyId() == null || oss.getAccessKeySecret() == null) {
            throw new FileException(FileException.Code.INVALID_BACKEND_CONFIG,
                    "平台 OSS 配置字段缺失（wen-saas-boot.file.platform-oss.*）");
        }
        OSS client = new OSSClientBuilder().build(
                oss.getEndpoint(), oss.getAccessKeyId(), oss.getAccessKeySecret());
        return new AliyunOssFileStorageAdapter(client, oss.getBucket(), oss.getEndpoint());
    }

    /** {@code config.endpoint} 存 COS region code（如 ap-guangzhou），非域名。 */
    private TencentCosFileStorageAdapter buildTencentCos(SysStorageConfig config) {
        if (config.getEndpoint() == null || config.getBucket() == null
                || config.getAccessKeyId() == null || config.getAccessKeySecret() == null) {
            throw new FileException(FileException.Code.INVALID_BACKEND_CONFIG,
                    "COS 配置字段缺失（endpoint/bucket/accessKeyId/accessKeySecret）");
        }
        COSClient client = buildCosClient(config.getEndpoint(), config.getAccessKeyId(), config.getAccessKeySecret());
        return new TencentCosFileStorageAdapter(client, config.getBucket(), config.getEndpoint());
    }

    private TencentCosFileStorageAdapter buildTencentCosFromProps() {
        FileProperties.PlatformTencentCos cos = props.getPlatformTencentCos();
        if (cos.getEndpoint() == null || cos.getBucket() == null
                || cos.getAccessKeyId() == null || cos.getAccessKeySecret() == null) {
            throw new FileException(FileException.Code.INVALID_BACKEND_CONFIG,
                    "平台 COS 配置字段缺失（wen-saas-boot.file.platform-tencent-cos.*）");
        }
        COSClient client = buildCosClient(cos.getEndpoint(), cos.getAccessKeyId(), cos.getAccessKeySecret());
        return new TencentCosFileStorageAdapter(client, cos.getBucket(), cos.getEndpoint());
    }

    private COSClient buildCosClient(String region, String secretId, String secretKey) {
        COSCredentials cred = new BasicCOSCredentials(secretId, secretKey);
        ClientConfig clientConfig = new ClientConfig(new Region(region));
        return new COSClient(cred, clientConfig);
    }
}
