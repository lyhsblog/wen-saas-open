package com.wensaas.core.impl.file;

import com.wensaas.core.api.file.FileBackendType;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/**
 * 文件存储框架配置。
 *
 * <p>多版本密钥支持，轮转时新增 key 切 active；旧密文按 keyId 路由解密：
 * <pre>
 * wen-saas-boot:
 *   file:
 *     enabled: true
 *     encryption-keys:
 *       default: ${WEN_SAAS_BOOT_FILE_AES_KEY_DEFAULT:dev-only-...}
 *       v2:      ${WEN_SAAS_BOOT_FILE_AES_KEY_V2}
 *     active-key-id: default
 *     max-size: 100MB
 *     download-url-ttl: 4m
 *     signed-url-ttl: 5m
 *     platform-default-backend: local
 *     local:
 *       root: ./data/files
 *     platform-oss:
 *       endpoint: oss-cn-hangzhou.aliyuncs.com
 *       bucket: xxx
 *       access-key-id: ...
 *       access-key-secret: ...
 *       path-prefix: wen-saas-boot
 *     platform-tencent-cos:
 *       endpoint: ap-guangzhou        # COS region code，非域名
 *       bucket: xxx-1250000000        # COS bucket 名形如 {name}-{appid}
 *       access-key-id: ...            # SecretId
 *       access-key-secret: ...        # SecretKey
 *       path-prefix: wen-saas-boot
 * </pre>
 */
@ConfigurationProperties(prefix = "wen-saas-boot.file")
@Getter
@Setter
public class FileProperties {

    /** 默认 keyId 约定；{@link #activeKeyId} 未配置时回退此值。 */
    public static final String DEFAULT_KEY_ID = "default";

    private boolean enabled = true;
    /** 多版本密钥；key=keyId，value=Base64 32 字节。 */
    private Map<String, String> encryptionKeys = new LinkedHashMap<>();
    /** 加密时使用的 keyId；必须存在于 {@link #encryptionKeys} 中。null/blank 时默认 default。 */
    private String activeKeyId;

    private DataSize maxSize = DataSize.ofMegabytes(100);
    /** 文件中心租户默认容量配额（sys_tenant.storage_cap 为 NULL 时回退此值）。 */
    private DataSize defaultQuota = DataSize.ofGigabytes(50);
    private Duration downloadUrlTtl = Duration.ofMinutes(4);
    private Duration signedUrlTtl = Duration.ofMinutes(5);
    private FileBackendType platformDefaultBackend = FileBackendType.LOCAL;

    private Local local = new Local();
    private PlatformOss platformOss = new PlatformOss();
    private PlatformTencentCos platformTencentCos = new PlatformTencentCos();

    /** 解析最终生效的密钥集合（{@link #encryptionKeys} 的副本）。 */
    public Map<String, String> resolveKeys() {
        if (encryptionKeys == null) {
            return new LinkedHashMap<>();
        }
        return new LinkedHashMap<>(encryptionKeys);
    }

    /**
     * 解析最终生效的 active keyId。优先使用 {@link #activeKeyId}；为空时回退 default。
     */
    public String resolveActiveKeyId() {
        if (activeKeyId != null && !activeKeyId.isBlank()) {
            return activeKeyId;
        }
        return DEFAULT_KEY_ID;
    }

    /** 本地存储后端配置。 */
    @Getter
    @Setter
    public static class Local {
        /** 本地存储根路径；相对路径以工作目录为基。 */
        private String root = "./data/files";

    }

    /** 平台默认 OSS（当 {@link #platformDefaultBackend} = aliyun_oss 时启用）。 */
    @Getter
    @Setter
    public static class PlatformOss {
        private String endpoint;
        private String bucket;
        private String accessKeyId;
        private String accessKeySecret;
        private String pathPrefix;

    }

    /**
     * 平台默认腾讯云 COS（当 {@link #platformDefaultBackend} = tencent_cos 时启用）。
     *
     * <p>字段语义复用 {@code sys_storage_config} 通用列命名：{@link #endpoint} 实为 COS region
     * code（如 {@code ap-guangzhou}），{@link #accessKeyId}/{@link #accessKeySecret} 对应
     * SecretId/SecretKey。
     */
    @Getter
    @Setter
    public static class PlatformTencentCos {
        private String endpoint;
        private String bucket;
        private String accessKeyId;
        private String accessKeySecret;
        private String pathPrefix;

    }
}
