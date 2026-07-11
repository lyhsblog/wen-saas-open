package com.wensaas.core.impl.openapi.key;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.tenant.TenantContext;
import com.wensaas.core.impl.file.CredentialCipher;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 租户端开放 API 凭证（AppKey）管理：签发 / 编辑 / 启停 / 密钥轮换。
 *
 * <p>secret 仅签发 / 轮换时以明文返回一次，落库走 {@link CredentialCipher} AES 加密。
 * 凭证不软删：撤销即置 {@code status='disabled'}（调用日志按 api_key_id 关联，保留可溯）。
 * 租户隔离由租户线拦截器自动叠加；网关侧 {@link ApiKeyResolver} 有 authCacheTtl（默认 5min）
 * 的 Redis 缓存，所有影响鉴权的写操作必须调 {@code resolver.evict} 让变更即时生效。
 */
public class ApiKeyAdminService {

    private static final int MAX_WHITELIST = 50;

    private final SysApiKeyMapper mapper;
    private final CredentialCipher cipher;
    private final ApiKeyResolver resolver;

    public ApiKeyAdminService(SysApiKeyMapper mapper, CredentialCipher cipher, ApiKeyResolver resolver) {
        this.mapper = mapper;
        this.cipher = cipher;
        this.resolver = resolver;
    }

    public List<ApiKeyView> list() {
        return mapper.selectList(new LambdaQueryWrapper<SysApiKey>()
                        .orderByDesc(SysApiKey::getCreatedAt))
                .stream().map(ApiKeyAdminService::toView).toList();
    }

    public CreatedApiKey create(String name, LocalDateTime expiresAt, List<String> ipWhitelist,
                                Integer rateLimitQps, Boolean requireSignature) {
        ApiKeyCredentialGenerator.GeneratedCredential cred = ApiKeyCredentialGenerator.generate();
        LocalDateTime now = LocalDateTime.now();
        SysApiKey row = new SysApiKey();
        row.setId(IdWorker.getId());
        row.setTenantId(TenantContext.require());
        row.setName(requireName(name));
        row.setApiKey(cred.apiKey());
        row.setSecretEncrypted(cipher.encrypt(cred.plainSecret()));
        row.setIpWhitelist(normalizeWhitelist(ipWhitelist));
        row.setRequireSignature(requireSignature == null || requireSignature);
        row.setExpiresAt(expiresAt);
        row.setRateLimitQps(validQps(rateLimitQps));
        row.setStatus("active");
        row.setCreatorId(UserContext.require().userId());
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        mapper.insert(row);
        return new CreatedApiKey(row.getId(), row.getName(), row.getApiKey(), cred.plainSecret());
    }

    public ApiKeyView update(Long id, String name, LocalDateTime expiresAt, List<String> ipWhitelist,
                             Integer rateLimitQps, Boolean requireSignature) {
        SysApiKey row = requireExisting(id);
        row.setName(requireName(name));
        row.setExpiresAt(expiresAt);
        row.setIpWhitelist(normalizeWhitelist(ipWhitelist));
        row.setRateLimitQps(validQps(rateLimitQps));
        if (requireSignature != null) {
            row.setRequireSignature(requireSignature);
        }
        row.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(row);
        resolver.evict(row.getApiKey());   // 网关缓存 5min，变更须即时生效
        return toView(row);
    }

    public ApiKeyView setStatus(Long id, boolean enabled) {
        SysApiKey row = requireExisting(id);
        row.setStatus(enabled ? "active" : "disabled");
        row.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(row);
        resolver.evict(row.getApiKey());   // 停用即时生效（否则缓存内旧凭证仍放行）
        return toView(row);
    }

    /** 重新生成 secret（旧值立即失效），明文仅本次返回。api_key 标识不变，调用方只换签名密钥。 */
    public CreatedApiKey rotateSecret(Long id) {
        SysApiKey row = requireExisting(id);
        String plain = ApiKeyCredentialGenerator.generatePlainSecret();
        row.setSecretEncrypted(cipher.encrypt(plain));
        row.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(row);
        resolver.evict(row.getApiKey());   // 旧 secret 立即失效
        return new CreatedApiKey(row.getId(), row.getName(), row.getApiKey(), plain);
    }

    private SysApiKey requireExisting(Long id) {
        SysApiKey row = mapper.selectById(id);
        if (row == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "API 凭证不存在");
        }
        return row;
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "名称必填");
        }
        return name.trim();
    }

    private static Integer validQps(Integer qps) {
        if (qps == null) {
            return null;
        }
        if (qps <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "限流配额须为正整数（留空=仅租户级限流）");
        }
        return qps;
    }

    private static List<String> normalizeWhitelist(List<String> list) {
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String s : list) {
            if (s != null && !s.isBlank() && !out.contains(s.trim())) {
                out.add(s.trim());
            }
        }
        if (out.size() > MAX_WHITELIST) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "IP 白名单条目超过上限 " + MAX_WHITELIST);
        }
        return out;
    }

    private static ApiKeyView toView(SysApiKey r) {
        return new ApiKeyView(r.getId(), r.getName(), r.getApiKey(), r.getStatus(),
                r.getRequireSignature(), r.getIpWhitelist(), r.getRateLimitQps(),
                r.getExpiresAt(), r.getLastUsedAt(), r.getCreatedAt());
    }

    // ============================== DTO ==============================

    public record ApiKeyView(Long id, String name, String apiKey, String status,
                             Boolean requireSignature, List<String> ipWhitelist, Integer rateLimitQps,
                             LocalDateTime expiresAt, LocalDateTime lastUsedAt, LocalDateTime createdAt) {
    }

    /** 签发 / 轮换返回：{@code secret} 为明文，仅此一次。 */
    public record CreatedApiKey(Long id, String name, String apiKey, String secret) {
    }
}
