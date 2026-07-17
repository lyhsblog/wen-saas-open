package com.wensaas.core.impl.tenant;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wensaas.core.api.file.FileException;
import com.wensaas.core.api.file.FileService;
import com.wensaas.core.api.publicapi.TenantPublicException;
import com.wensaas.core.api.tenant.CrossTenantQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 公开租户信息查询编排。
 *
 * <p>从 {@code TenantPublicController} 抽回 core-impl：跨租户按 code / 旧路由位别名解析租户，并实时签发
 * Logo 下载 URL。{@code @CrossTenantQuery} 收到方法级（保持窄作用域），主查询只圈这次解析。
 */
@Service
public class TenantPublicQueryService {

    private static final Logger log = LoggerFactory.getLogger(TenantPublicQueryService.class);
    /** 公开 Logo URL 有效期（签名 URL 自带 HMAC，免登录可下载）。 */
    private static final Duration LOGO_URL_TTL = Duration.ofHours(1);

    private final SysTenantMapper tenantMapper;
    private final SysTenantRouteAliasMapper routeAliasMapper;
    private final FileService fileService;

    public TenantPublicQueryService(SysTenantMapper tenantMapper,
                                    SysTenantRouteAliasMapper routeAliasMapper,
                                    FileService fileService) {
        this.tenantMapper = tenantMapper;
        this.routeAliasMapper = routeAliasMapper;
        this.fileService = fileService;
    }

    /** 公开租户视图（控制器再映射为 web DTO；canonicalCode 即 code）。 */
    public record PublicTenant(String id, String code, String name, String logoUrl, String status) {}

    @CrossTenantQuery("TenantPublicQueryService.byCode: 公开接口无租户上下文，按 code 跨租户解析租户")
    public PublicTenant byCode(String code) {
        SysTenant tenant = tenantMapper.selectOne(
                new LambdaQueryWrapper<SysTenant>().eq(SysTenant::getCode, code));
        if (tenant == null) {
            // 当前 code 未命中：尝试 90 天内的旧路由位别名重定向
            tenant = resolveByAlias(code);
        }
        if (tenant == null) {
            throw new TenantPublicException(TenantPublicException.Code.TENANT_NOT_FOUND,
                    "租户不存在：code=" + code);
        }
        return new PublicTenant(
                String.valueOf(tenant.getId()),
                tenant.getCode(),
                tenant.getName(),
                resolveLogoUrl(tenant),
                statusOf(tenant.getStatus()));
    }

    /** 旧路由位别名解析：old_code 命中且未过期 → 返回对应租户当前行；否则 null。 */
    private SysTenant resolveByAlias(String code) {
        SysTenantRouteAlias alias = routeAliasMapper.selectOne(
                new LambdaQueryWrapper<SysTenantRouteAlias>()
                        .eq(SysTenantRouteAlias::getOldCode, code)
                        .gt(SysTenantRouteAlias::getExpiresAt, LocalDateTime.now()));
        if (alias == null) {
            return null;
        }
        return tenantMapper.selectById(alias.getTenantId());
    }

    /** 优先实时签发 logoFileId 的下载 URL（免登录可下），回退历史 logo_url 列。 */
    private String resolveLogoUrl(SysTenant tenant) {
        String logoFileId = tenant.getLogoFileId();
        if (logoFileId != null) {
            try {
                return fileService.signedDownloadUrl(logoFileId, LOGO_URL_TTL);
            } catch (FileException e) {
                log.warn("[TenantPublic] 签发 Logo URL 失败 tenant={} fileId={}: {}",
                        tenant.getId(), logoFileId, e.toString());
            }
        }
        return tenant.getLogoUrl();
    }

    private static String statusOf(Integer status) {
        if (status == null) {
            return "active";
        }
        return switch (status) {
            case 0 -> "paused";
            case 1 -> "active";
            case 2 -> "expired";
            default -> "active";
        };
    }
}
