package com.wensaas.core.impl.file.storage;

import com.aliyun.oss.ClientBuilderConfiguration;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.OSSException;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.exception.CosServiceException;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.region.Region;
import com.wensaas.core.api.audit.AuditLogRecorder;
import com.wensaas.core.api.file.FileBackendType;
import com.wensaas.core.api.file.FileException;
import com.wensaas.core.api.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.wensaas.core.impl.file.CredentialCipher;

/**
 * 存储后端配置管理。运营 / 租户主管理员通过它管理 OSS 凭证。
 *
 * <p>所有写方法 {@link Transactional}；走 MP TenantLine 自动 tenant_id 注入。
 *
 * <p>凭证生命周期：
 * <ul>
 *   <li>写入：accessKeySecret 在 manager 内 encrypt → 落 sys_storage_config.access_key_secret 列</li>
 *   <li>读出：select 后调用 decrypt 才能拿明文</li>
 *   <li>切换：旧 active → archived；新 insert → active；旧 OSS adapter 立刻 evict 释放</li>
 * </ul>
 */
public class StorageConfigManager {

    private static final Logger log = LoggerFactory.getLogger(StorageConfigManager.class);
    private static final String AUDIT_MODULE = "sys.storage";
    /** secret 回显掩码；请求体携带该占位表示「沿用已保存的凭证」。 */
    public static final String SECRET_MASK = "********";
    /** 探活临时客户端超时（毫秒）——探活是同步接口，必须几秒内出结果，别让页面长挂。 */
    private static final int PROBE_CONNECT_TIMEOUT_MS = 5_000;
    private static final int PROBE_SOCKET_TIMEOUT_MS = 8_000;

    private final SysStorageConfigMapper mapper;
    private final CredentialCipher cipher;
    private final FileStorageFactory factory;
    private final AuditLogRecorder auditRecorder;

    public StorageConfigManager(SysStorageConfigMapper mapper,
                                CredentialCipher cipher,
                                FileStorageFactory factory,
                                AuditLogRecorder auditRecorder) {
        this.mapper = mapper;
        this.cipher = cipher;
        this.factory = factory;
        this.auditRecorder = auditRecorder;
    }

    /** 当前租户所有存储配置（按 created_at DESC）；secret 字段一律掩码为 {@link #SECRET_MASK}。 */
    public List<SysStorageConfig> listForTenant() {
        Long tenantId = TenantContext.require();
        List<SysStorageConfig> list = mapper.selectAllForTenant(tenantId);
        list.forEach(c -> c.setAccessKeySecret(SECRET_MASK));
        return list;
    }

    /** 当前租户 active 配置；null 表示走平台默认。secret 字段掩码。 */
    public SysStorageConfig getActive() {
        Long tenantId = TenantContext.require();
        SysStorageConfig active = mapper.selectActive(tenantId);
        if (active != null) {
            active.setAccessKeySecret(SECRET_MASK);
        }
        return active;
    }

    /**
     * 真实探活（无租户上下文要求）：put 小测试对象 → delete。
     * local 直接写删本地磁盘；oss / cos 用 draft 凭证构造短超时临时客户端。
     *
     * @return null = 成功；否则返回分类后的失败原因（网络 / 鉴权 / 桶不存在等），可直接给前端展示。
     */
    public String probe(StorageConfigDraft draft) {
        try {
            doProbe(draft);
            return null;
        } catch (Exception ex) {
            log.warn("[StorageConfig] {} 探活失败 endpoint={} bucket={}: {}",
                    draft.backendType().code(), draft.endpoint(), draft.bucket(), ex.toString());
            return describeProbeFailure(draft.backendType(), ex);
        }
    }

    /**
     * 测试连接（租户端语义）：secret 传 {@link #SECRET_MASK} 时先替换为当前 active 配置的已存凭证，
     * 再走 {@link #probe} 同一套 put/delete 烟测。失败记审计并抛 {@link FileException}
     * code = {@code TEST_CONNECTION_FAILED}。local 为 dev 内部兜底，跳过测试。
     */
    public void testConnection(StorageConfigDraft draft) {
        if (draft.backendType() == FileBackendType.LOCAL) {
            // local 后端无需测试
            return;
        }
        StorageConfigDraft effective = resolveMaskedSecret(draft);
        try {
            doProbe(effective);
        } catch (Exception ex) {
            try {
                Map<String, Object> failure = new LinkedHashMap<>();
                failure.put("endpoint", draft.endpoint());
                failure.put("bucket", draft.bucket());
                failure.put("backendType", draft.backendType().code());
                failure.put("reason", ex.getMessage());
                auditRecorder.recordCreate(AUDIT_MODULE, "storage_config",
                        null, "测试连接失败", failure);
            } catch (Exception auditEx) {
                log.warn("[StorageConfig] 审计写入失败: {}", auditEx.toString());
            }
            // 原始异常（含 java.* 类名 / 栈细节）记日志供排查，不透传到前端；UI 看分类后的业务提示。
            log.warn("[StorageConfig] {} 测试连接失败 endpoint={} bucket={}: {}",
                    draft.backendType().code(), draft.endpoint(), draft.bucket(), ex.toString());
            throw new FileException(FileException.Code.TEST_CONNECTION_FAILED,
                    describeProbeFailure(draft.backendType(), ex), ex);
        }
    }

    /** 执行真实 put/delete 烟测；失败抛原始异常，由调用方分类 / 包装。 */
    private void doProbe(StorageConfigDraft draft) {
        // 随机测试 key：__conn_test__ 前缀便于识别，UUID 防并发碰撞；成功即删不留残留
        String testKey = "__conn_test__" + UUID.randomUUID();
        switch (draft.backendType()) {
            case LOCAL -> {
                var local = factory.localAdapter();
                byte[] payload = "wen-saas-boot connection test\n".getBytes();
                local.upload(testKey, new ByteArrayInputStream(payload), payload.length, "text/plain");
                local.delete(testKey);
            }
            case ALIYUN_OSS -> testAliyunOssConnection(draft, testKey);
            case TENCENT_COS -> testTencentCosConnection(draft, testKey);
        }
    }

    /** 探活失败原因分类：网络 / 鉴权 / 桶不存在等，产出可直接展示的业务描述（不含类名 / 栈细节）。 */
    private static String describeProbeFailure(FileBackendType backendType, Exception ex) {
        String vendor = switch (backendType) {
            case ALIYUN_OSS -> "OSS";
            case TENCENT_COS -> "COS";
            case LOCAL -> "本地存储";
        };
        String errorCode = null;
        String serviceMsg = null;
        if (ex instanceof OSSException oss) {
            errorCode = oss.getErrorCode();
            serviceMsg = oss.getErrorMessage();
        } else if (ex instanceof CosServiceException cos) {
            errorCode = cos.getErrorCode();
            serviceMsg = cos.getErrorMessage();
        }
        if (errorCode != null) {
            return switch (errorCode) {
                case "NoSuchBucket" -> vendor + " Bucket 不存在，请检查 Bucket 名称";
                case "InvalidAccessKeyId" -> vendor + " AccessKey 无效，请检查 AccessKey ID";
                case "SignatureDoesNotMatch" -> vendor + " 凭证签名不匹配，请检查 AccessKey Secret";
                case "AccessDenied" -> vendor + " 拒绝访问（AccessDenied），请检查凭证对该 Bucket 的读写权限";
                case "RequestTimeTooSkewed" -> "服务器时钟偏差过大（RequestTimeTooSkewed），请校准本机时间";
                default -> vendor + " 服务端拒绝：" + errorCode
                        + (serviceMsg == null || serviceMsg.isBlank() ? "" : "（" + serviceMsg + "）");
            };
        }
        if (ex instanceof com.aliyun.oss.ClientException
                || ex instanceof com.qcloud.cos.exception.CosClientException) {
            return vendor + " 网络连接失败，请检查 "
                    + (backendType == FileBackendType.TENCENT_COS ? "Region（形如 ap-guangzhou）" : "Endpoint（形如 oss-cn-<region>.aliyuncs.com）")
                    + " 与网络连通性";
        }
        if (ex instanceof FileException fe) {
            return vendor + " 探活失败：" + fe.getMessage();
        }
        String msg = ex.getMessage();
        return vendor + " 探活失败" + (msg == null || msg.isBlank() ? "" : "：" + msg);
    }

    /**
     * secret 为掩码占位时替换成当前 active 配置的已存凭证（解密后）；
     * 无已存配置则报错要求重新输入。非掩码原样返回。
     */
    private StorageConfigDraft resolveMaskedSecret(StorageConfigDraft draft) {
        if (!SECRET_MASK.equals(draft.accessKeySecret())) {
            return draft;
        }
        Long tenantId = TenantContext.require();
        SysStorageConfig active = mapper.selectActive(tenantId);
        if (active == null || active.getAccessKeySecret() == null) {
            throw new FileException(FileException.Code.INVALID_BACKEND_CONFIG,
                    "没有已保存的凭证可沿用，请输入 AccessKey Secret");
        }
        return new StorageConfigDraft(draft.backendType(), draft.endpoint(), draft.bucket(),
                draft.accessKeyId(), cipher.decrypt(active.getAccessKeySecret()), draft.pathPrefix());
    }

    private void testAliyunOssConnection(StorageConfigDraft draft, String testKey) {
        OSS client = null;
        try {
            ClientBuilderConfiguration conf = new ClientBuilderConfiguration();
            conf.setConnectionTimeout(PROBE_CONNECT_TIMEOUT_MS);
            conf.setSocketTimeout(PROBE_SOCKET_TIMEOUT_MS);
            conf.setMaxErrorRetry(0);
            client = new OSSClientBuilder().build(
                    draft.endpoint(), draft.accessKeyId(), draft.accessKeySecret(), conf);
            byte[] payload = "wen-saas-boot connection test\n".getBytes();
            client.putObject(draft.bucket(), testKey, new ByteArrayInputStream(payload));
            client.deleteObject(draft.bucket(), testKey);
        } finally {
            if (client != null) {
                try { client.shutdown(); } catch (Exception ignore) { /* ignore */ }
            }
        }
    }

    /** {@code draft.endpoint()} 存 COS region code（如 ap-guangzhou），非域名。 */
    private void testTencentCosConnection(StorageConfigDraft draft, String testKey) {
        COSClient client = null;
        try {
            COSCredentials cred = new BasicCOSCredentials(draft.accessKeyId(), draft.accessKeySecret());
            ClientConfig clientConfig = new ClientConfig(new Region(draft.endpoint()));
            clientConfig.setConnectionTimeout(PROBE_CONNECT_TIMEOUT_MS);
            clientConfig.setSocketTimeout(PROBE_SOCKET_TIMEOUT_MS);
            clientConfig.setMaxErrorRetry(0);
            client = new COSClient(cred, clientConfig);
            byte[] payload = "wen-saas-boot connection test\n".getBytes();
            ObjectMetadata meta = new ObjectMetadata();
            meta.setContentLength(payload.length);
            client.putObject(draft.bucket(), testKey, new ByteArrayInputStream(payload), meta);
            client.deleteObject(draft.bucket(), testKey);
        } finally {
            if (client != null) {
                try { client.shutdown(); } catch (Exception ignore) { /* ignore */ }
            }
        }
    }

    /**
     * 切换到自有 OSS。流程：
     * <ol>
     *   <li>{@link #testConnection} 必须通过</li>
     *   <li>当前 active 全部 → archived（DB partial unique index 兜底）</li>
     *   <li>insert 新 active（accessKeySecret 加密）</li>
     *   <li>主动 evict 旧 active 对应的 OSS adapter</li>
     *   <li>调 {@link AuditLogRecorder} 记录变更（不含 secret）</li>
     * </ol>
     */
    @Transactional
    public SysStorageConfig switchToCustomOss(StorageConfigDraft draft) {
        // secret 传掩码 = 沿用当前 active 凭证；先替换再测试 / 加密落库
        draft = resolveMaskedSecret(draft);
        testConnection(draft);
        Long tenantId = TenantContext.require();

        SysStorageConfig prior = mapper.selectActive(tenantId);
        mapper.archiveAllActive(tenantId);

        SysStorageConfig record = new SysStorageConfig();
        record.setTenantId(tenantId);
        record.setBackendType(draft.backendType().code());
        record.setEndpoint(draft.endpoint());
        record.setBucket(draft.bucket());
        record.setAccessKeyId(draft.accessKeyId());
        record.setAccessKeySecret(cipher.encrypt(draft.accessKeySecret()));
        record.setPathPrefix(draft.pathPrefix());
        record.setStatus("active");
        mapper.insert(record);

        if (prior != null) {
            factory.evict(prior.getId());
        }

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("backendType", draft.backendType().code());
        after.put("endpoint", draft.endpoint());
        after.put("bucket", draft.bucket());
        after.put("accessKeyId", draft.accessKeyId());
        after.put("pathPrefix", draft.pathPrefix());
        // 明确不含 accessKeySecret
        auditRecorder.recordCreate(AUDIT_MODULE, "storage_config",
                String.valueOf(record.getId()), "切换到自有对象存储", after);
        log.info("[StorageConfig] tenant={} switched to custom storage backend={}, configId={}",
                tenantId, draft.backendType().code(), record.getId());
        return record;
    }

    /** 切换回平台默认存储。旧 active 全部 archived；evict 对应 adapter。 */
    @Transactional
    public void switchToPlatform() {
        Long tenantId = TenantContext.require();
        SysStorageConfig prior = mapper.selectActive(tenantId);
        if (prior == null) {
            // 已是平台默认，no-op
            return;
        }
        mapper.archiveAllActive(tenantId);
        factory.evict(prior.getId());

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("priorConfigId", prior.getId());
        after.put("backendType", "platform-default");
        auditRecorder.recordCreate(AUDIT_MODULE, "storage_config",
                null, "切换回平台默认存储", after);
        log.info("[StorageConfig] tenant={} switched to platform default (prior configId={})",
                tenantId, prior.getId());
    }

    /**
     * 存储配置 draft（input dto）。客户端 / UI 会话填入新凭证时使用。
     *
     * @param backendType   {@code local} 仅 dev，正式 {@code aliyun_oss} / {@code tencent_cos}
     * @param endpoint      OSS 为域名 endpoint；COS 为 region code（如 ap-guangzhou）
     * @param accessKeyId   OSS 为 AccessKeyId；COS 为 SecretId
     * @param accessKeySecret 明文；OSS 为 AccessKeySecret，COS 为 SecretKey；manager 内部加密落库
     */
    public record StorageConfigDraft(
            FileBackendType backendType,
            String endpoint,
            String bucket,
            String accessKeyId,
            String accessKeySecret,
            String pathPrefix) {
        public StorageConfigDraft {
            if (backendType == null) {
                throw new IllegalArgumentException("backendType 必填");
            }
            if (backendType == FileBackendType.ALIYUN_OSS || backendType == FileBackendType.TENCENT_COS) {
                if (endpoint == null || endpoint.isBlank()) {
                    throw new IllegalArgumentException(backendType.code() + " 时 endpoint 必填");
                }
                if (bucket == null || bucket.isBlank()) {
                    throw new IllegalArgumentException(backendType.code() + " 时 bucket 必填");
                }
                if (accessKeyId == null || accessKeyId.isBlank()) {
                    throw new IllegalArgumentException(backendType.code() + " 时 accessKeyId 必填");
                }
                if (accessKeySecret == null || accessKeySecret.isBlank()) {
                    throw new IllegalArgumentException(backendType.code() + " 时 accessKeySecret 必填");
                }
            }
        }
    }
}
