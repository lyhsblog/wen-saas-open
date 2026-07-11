package com.wensaas.core.impl.openapi.key;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.plugins.IgnoreStrategy;
import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;
import com.wensaas.core.api.openapi.OpenApiAuthenticationException;
import com.wensaas.core.impl.file.CredentialCipher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;
import com.wensaas.core.impl.openapi.OpenApiProperties;

/**
 * 按 api_key 查 {@link SysApiKey}（Redis 缓存 5min + DB fallback），并对 {@code status} / {@code expires_at}
 * 做基础校验；解密 secret 的工作交给调用方按需触发（仅 HMAC 签名场景需要）。
 *
 * <p>查询绕过 MP TenantLine：开放 API 请求进入鉴权 Filter 时 TenantContext 尚未建立。
 *
 * <p>Redis key = {@code wen-saas-boot:openapi:apikey:{api_key}}；TTL = {@code authCacheTtl}（默认 5min）；
 * 凭证撤销 / 禁用 / 轮换 / 编辑时由管理面（{@code ApiKeyAdminService}）主动 {@link #evict(String)}。
 *
 * <p>{@code last_used_at} 借缓存 miss 节流回写：仅 DB 加载路径 UPDATE 一次（即每个凭证至多每
 * authCacheTtl 一次 DB 写），语义是"最近使用"粒度而非精确到次。
 */
public class ApiKeyResolver {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyResolver.class);
    private static final String KEY_PREFIX = "wen-saas-boot:openapi:apikey:";
    private static final IgnoreStrategy IGNORE_TENANT_LINE =
            IgnoreStrategy.builder().tenantLine(true).build();

    private final SysApiKeyMapper mapper;
    private final CredentialCipher cipher;
    private final StringRedisTemplate redis;
    private final OpenApiProperties props;

    public ApiKeyResolver(SysApiKeyMapper mapper, CredentialCipher cipher,
                          StringRedisTemplate redis, OpenApiProperties props) {
        this.mapper = mapper;
        this.cipher = cipher;
        this.redis = redis;
        this.props = props;
    }

    /**
     * 查询并校验凭证。命中走 Redis → DB；status 非 active 抛 KEY_DISABLED；
     * expires_at 在过去抛 KEY_EXPIRED；未找到抛 INVALID_API_KEY。
     */
    public SysApiKey resolve(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new OpenApiAuthenticationException(
                    OpenApiAuthenticationException.Code.MISSING_HEADER, "缺少 X-Wen-API-Key");
        }
        SysApiKey key = loadFromCacheOrDb(apiKey);
        if (key == null) {
            throw new OpenApiAuthenticationException(
                    OpenApiAuthenticationException.Code.INVALID_API_KEY, "凭证不存在");
        }
        if (!"active".equals(key.getStatus())) {
            throw new OpenApiAuthenticationException(
                    OpenApiAuthenticationException.Code.KEY_DISABLED, "凭证已禁用");
        }
        if (key.getExpiresAt() != null && key.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new OpenApiAuthenticationException(
                    OpenApiAuthenticationException.Code.KEY_EXPIRED, "凭证已过期");
        }
        return key;
    }

    /** 解密凭证 secret，供 HMAC 签名验证现场使用。明文不缓存。 */
    public String decryptSecret(SysApiKey key) {
        return cipher.decrypt(key.getSecretEncrypted());
    }

    /** 凭证撤销 / 禁用时主动失效缓存。 */
    public void evict(String apiKey) {
        if (redis != null) {
            redis.delete(KEY_PREFIX + apiKey);
        }
    }

    private SysApiKey loadFromCacheOrDb(String apiKey) {
        if (redis != null) {
            try {
                String json = redis.opsForValue().get(KEY_PREFIX + apiKey);
                if (json != null) {
                    return JSON.parseObject(json, SysApiKey.class);
                }
            } catch (RuntimeException e) {
                log.warn("apikey.cache.read.fail apiKey={} reason={}", apiKey, e.getMessage());
            }
        }
        SysApiKey key;
        InterceptorIgnoreHelper.handle(IGNORE_TENANT_LINE);
        try {
            key = mapper.selectActiveByApiKey(apiKey);
            if (key != null) {
                // last_used_at 节流回写（缓存 miss 才走到这里，写频率 ≤ 每 authCacheTtl 一次）
                mapper.touchLastUsed(key.getId());
            }
        } finally {
            InterceptorIgnoreHelper.clearIgnoreStrategy();
        }
        if (key != null && redis != null) {
            try {
                redis.opsForValue().set(KEY_PREFIX + apiKey, JSON.toJSONString(key),
                        props.getAuthCacheTtl());
            } catch (RuntimeException e) {
                log.warn("apikey.cache.write.fail apiKey={} reason={}", apiKey, e.getMessage());
            }
        }
        return key;
    }
}
