package com.wensaas.core.impl.tenant.selfservice;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.file.FileService;
import com.wensaas.core.api.file.FileUploadRequest;
import com.wensaas.core.api.file.FileUploadResult;
import com.wensaas.core.api.file.FileVisibility;
import com.wensaas.core.api.tenant.selfservice.TenantCompanyException;
import com.wensaas.core.api.tenant.selfservice.TenantCompanyService;
import com.wensaas.core.api.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import com.wensaas.core.impl.tenant.SysTenant;
import com.wensaas.core.impl.tenant.SysTenantMapper;
import com.wensaas.core.impl.tenant.TenantCodePolicy;
import com.wensaas.core.impl.tenant.SysTenantRouteAlias;
import com.wensaas.core.impl.tenant.SysTenantRouteAliasMapper;

/**
 * 租户自助公司信息实现。运行在当前租户上下文内（{@link TenantContext#require()}）。
 *
 * <p>{@code sys_tenant} 为平台白名单表，更新不经 MP TenantLine；但本服务只读写
 * {@code TenantContext.require()} 取到的当前租户行，且路由位唯一性跨全表校验，故安全。
 */
public class TenantCompanyServiceImpl implements TenantCompanyService {

    private static final Logger log = LoggerFactory.getLogger(TenantCompanyServiceImpl.class);

    private static final int ROUTE_SLOT_COOLDOWN_DAYS = 30;
    /** 旧路由位重定向有效期：改后 90 天内旧地址自动重定向到新地址。 */
    private static final int ROUTE_ALIAS_TTL_DAYS = 90;
    private static final long LOGO_MAX_BYTES = 1024 * 1024L;
    private static final Set<String> LOGO_MIME = Set.of(
            "image/png", "image/jpeg", "image/jpg", "image/svg+xml");
    private static final Duration LOGO_URL_TTL = Duration.ofHours(1);

    private final SysTenantMapper tenantMapper;
    private final FileService fileService;
    private final SysTenantRouteAliasMapper routeAliasMapper;

    public TenantCompanyServiceImpl(SysTenantMapper tenantMapper, FileService fileService,
                                    SysTenantRouteAliasMapper routeAliasMapper) {
        this.tenantMapper = tenantMapper;
        this.fileService = fileService;
        this.routeAliasMapper = routeAliasMapper;
    }

    @Override
    public CompanyProfile getProfile() {
        long tenantId = TenantContext.require();
        SysTenant tenant = requireTenant(tenantId);
        return toProfile(tenant);
    }

    @Override
    @Transactional
    public void updateBasic(UpdateBasicCommand cmd) {
        long tenantId = TenantContext.require();
        requireTenant(tenantId);
        tenantMapper.update(null, new LambdaUpdateWrapper<SysTenant>()
                .eq(SysTenant::getId, tenantId)
                .set(SysTenant::getIndustry, cmd.industry())
                .set(SysTenant::getScale, cmd.scale())
                .set(SysTenant::getDescription, cmd.description())
                .set(SysTenant::getUpdatedBy, UserContext.require().userId()));
    }

    @Override
    @Transactional
    public void changeRouteSlot(String newSlot) {
        long tenantId = TenantContext.require();
        SysTenant tenant = requireTenant(tenantId);

        String slot = newSlot == null ? "" : newSlot.trim();
        if (!TenantCodePolicy.isValidFormat(slot)) {
            throw new TenantCompanyException(TenantCompanyException.Code.INVALID_ROUTE_SLOT,
                    "子域名须小写字母或数字开头，由小写字母、数字、连字符组成，不以连字符结尾，长度 5-16 位");
        }
        if (slot.equals(tenant.getCode())) {
            return; // 未变化，幂等
        }
        Instant changeableAt = changeableAt(tenant.getCodeChangedAt());
        if (changeableAt != null && changeableAt.isAfter(Instant.now())) {
            throw new TenantCompanyException(TenantCompanyException.Code.ROUTE_SLOT_CHANGE_TOO_SOON,
                    "子域名 30 天内只能修改一次，下次可修改时间：" + changeableAt);
        }
        long taken = tenantMapper.selectCount(new LambdaQueryWrapper<SysTenant>()
                .eq(SysTenant::getCode, slot)
                .ne(SysTenant::getId, tenantId));
        if (taken > 0) {
            throw new TenantCompanyException(TenantCompanyException.Code.ROUTE_SLOT_TAKEN,
                    "该子域名已被使用：" + slot);
        }
        tenantMapper.update(null, new LambdaUpdateWrapper<SysTenant>()
                .eq(SysTenant::getId, tenantId)
                .set(SysTenant::getCode, slot)
                .set(SysTenant::getCodeChangedAt, Instant.now())
                .set(SysTenant::getUpdatedBy, UserContext.require().userId()));
        registerRouteAlias(tenantId, tenant.getCode(), slot);
        log.info("[CompanyProfile] tenant={} route slot {} -> {}", tenantId, tenant.getCode(), slot);
    }

    /**
     * 登记旧路由位重定向别名：旧 code 90 天内重定向到新 code。
     *
     * <p>old_code 全局唯一，delete-then-insert 实现 upsert（覆盖历史/过期别名，兼容跨 DB）；
     * 同时清掉 old_code = 新 code 的陈旧别名（避免重定向回环、释放被占别名）。
     */
    private void registerRouteAlias(long tenantId, String oldCode, String newCode) {
        routeAliasMapper.delete(new LambdaQueryWrapper<SysTenantRouteAlias>()
                .eq(SysTenantRouteAlias::getOldCode, oldCode)
                .or().eq(SysTenantRouteAlias::getOldCode, newCode));
        SysTenantRouteAlias alias = new SysTenantRouteAlias();
        alias.setTenantId(tenantId);
        alias.setOldCode(oldCode);
        alias.setExpiresAt(LocalDateTime.now().plusDays(ROUTE_ALIAS_TTL_DAYS));
        alias.setCreatedAt(LocalDateTime.now());
        routeAliasMapper.insert(alias);
    }

    @Override
    @Transactional
    public CompanyProfile uploadLogo(LogoUploadCommand cmd) {
        long tenantId = TenantContext.require();
        requireTenant(tenantId);
        if (cmd.sizeBytes() > LOGO_MAX_BYTES) {
            throw new TenantCompanyException(TenantCompanyException.Code.INVALID_LOGO,
                    "Logo 文件大小不能超过 1MB");
        }
        String mime = cmd.mimeType() == null ? "" : cmd.mimeType().toLowerCase();
        if (!LOGO_MIME.contains(mime)) {
            throw new TenantCompanyException(TenantCompanyException.Code.INVALID_LOGO,
                    "Logo 仅支持 PNG / JPG / SVG 格式");
        }
        FileUploadResult result = fileService.uploadDirect(new FileUploadRequest(
                cmd.fileName(), cmd.mimeType(), cmd.sizeBytes(), cmd.content(),
                FileVisibility.FILE_CENTER, null, null, null, "/company-logo"));
        tenantMapper.update(null, new LambdaUpdateWrapper<SysTenant>()
                .eq(SysTenant::getId, tenantId)
                .set(SysTenant::getLogoFileId, result.fileId())
                .set(SysTenant::getUpdatedBy, UserContext.require().userId()));
        return toProfile(requireTenant(tenantId));
    }

    @Override
    @Transactional
    public void removeLogo() {
        long tenantId = TenantContext.require();
        SysTenant tenant = requireTenant(tenantId);
        if (tenant.getLogoFileId() != null) {
            try {
                fileService.delete(tenant.getLogoFileId());
            } catch (RuntimeException e) {
                log.warn("[CompanyProfile] tenant={} 删除旧 Logo 文件失败（忽略）: {}", tenantId, e.toString());
            }
        }
        tenantMapper.update(null, new LambdaUpdateWrapper<SysTenant>()
                .eq(SysTenant::getId, tenantId)
                .set(SysTenant::getLogoFileId, null)
                .set(SysTenant::getUpdatedBy, UserContext.require().userId()));
    }

    private CompanyProfile toProfile(SysTenant tenant) {
        String logoUrl = null;
        String logoFileId = tenant.getLogoFileId();
        if (logoFileId != null) {
            try {
                logoUrl = fileService.signedDownloadUrl(logoFileId, LOGO_URL_TTL);
            } catch (RuntimeException e) {
                log.warn("[CompanyProfile] tenant={} Logo 签名 URL 生成失败（回退默认）: {}",
                        tenant.getId(), e.toString());
            }
        }
        return new CompanyProfile(
                String.valueOf(tenant.getId()),
                tenant.getName(),
                tenant.getCode(),
                tenant.getFullName(),
                tenant.getIndustry(),
                tenant.getScale(),
                tenant.getDescription(),
                logoFileId,
                logoUrl,
                tenant.getCodeChangedAt(),
                changeableAt(tenant.getCodeChangedAt()));
    }

    private static Instant changeableAt(Instant codeChangedAt) {
        if (codeChangedAt == null) {
            return null;
        }
        return codeChangedAt.plus(ROUTE_SLOT_COOLDOWN_DAYS, ChronoUnit.DAYS);
    }

    private SysTenant requireTenant(long tenantId) {
        SysTenant tenant = tenantMapper.selectById(tenantId);
        if (tenant == null) {
            throw new TenantCompanyException(TenantCompanyException.Code.TENANT_NOT_FOUND,
                    "租户不存在：id=" + tenantId);
        }
        return tenant;
    }
}
