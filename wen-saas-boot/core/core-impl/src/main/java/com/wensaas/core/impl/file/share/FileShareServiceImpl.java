package com.wensaas.core.impl.file.share;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.auth.user.UserPrincipal;
import com.wensaas.core.api.file.FileException;
import com.wensaas.core.api.file.FileService;
import com.wensaas.core.api.file.FileMetadata;
import com.wensaas.core.api.file.share.FileShareConsumeResult;
import com.wensaas.core.api.file.share.FileShareCreateRequest;
import com.wensaas.core.api.file.share.FileShareInfo;
import com.wensaas.core.api.file.share.FileShareService;
import com.wensaas.core.api.tenant.TenantContext;
import com.wensaas.core.impl.auth.password.PasswordHasher;
import com.wensaas.core.impl.file.FileProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import com.wensaas.core.impl.file.FilePermissionEvaluator;

/**
 * {@link FileShareService} 实现。
 *
 * <p>关键点：
 * <ul>
 *   <li>跨租户 / 已软删 → {@link FileException.Code#SHARE_NOT_FOUND}（不暴露存在性）</li>
 *   <li>密码 BCrypt 哈希（复用 v1 {@link PasswordHasher} Bean）</li>
 *   <li>{@link #consumeShare} 走 mapper.tryConsume 原子 UPDATE WHERE ... AND download_count &lt; max</li>
 * </ul>
 */
public class FileShareServiceImpl implements FileShareService {

    private static final Logger log = LoggerFactory.getLogger(FileShareServiceImpl.class);

    private final SysFileShareMapper mapper;
    private final FileService fileService;
    private final PasswordHasher passwordHasher;
    private final FileProperties props;

    public FileShareServiceImpl(SysFileShareMapper mapper,
                                 FileService fileService,
                                 PasswordHasher passwordHasher,
                                 FileProperties props) {
        this.mapper = mapper;
        this.fileService = fileService;
        this.passwordHasher = passwordHasher;
        this.props = props;
    }

    @Override
    @Transactional
    public FileShareInfo createShare(FileShareCreateRequest request) {
        Long tenantId = TenantContext.require();
        // 校验文件存在 + 属于当前租户（FileService.metadata 走 MP TenantLine 自动隔离）
        FileMetadata file = fileService.metadata(request.fileId());

        SysFileShare entity = new SysFileShare();
        entity.setTenantId(tenantId);
        entity.setShareToken(generateShareToken());
        entity.setFileId(request.fileId());
        if (request.expiresAfter() != null) {
            entity.setExpiresAt(LocalDateTime.now().plus(request.expiresAfter()));
        }
        if (request.passwordPlain() != null && !request.passwordPlain().isBlank()) {
            entity.setPasswordHash(passwordHasher.hash(request.passwordPlain()));
        }
        entity.setMaxDownloads(request.maxDownloads());
        entity.setDownloadCount(0);
        entity.setStatus("active");
        LocalDateTime now = LocalDateTime.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        UserPrincipal principal = UserContext.get();
        if (principal != null) {
            entity.setCreatedBy(principal.userId());
            entity.setUpdatedBy(principal.userId());
        }
        mapper.insert(entity);
        return toInfo(entity, file);
    }

    @Override
    public FileShareInfo getShare(String shareToken) {
        SysFileShare entity = loadAndValidate(shareToken);
        FileMetadata file = fileService.metadata(entity.getFileId());
        return toInfo(entity, file);
    }

    @Override
    public FileShareConsumeResult consumeShare(String shareToken, String passwordPlain) {
        SysFileShare entity = loadAndValidate(shareToken);
        if (entity.getPasswordHash() != null) {
            if (passwordPlain == null || passwordPlain.isBlank()) {
                throw new FileException(FileException.Code.SHARE_PASSWORD_REQUIRED,
                        "该分享需要密码");
            }
            if (!passwordHasher.verify(passwordPlain, entity.getPasswordHash())) {
                throw new FileException(FileException.Code.SHARE_PASSWORD_WRONG, "密码错误");
            }
        }
        int affected = mapper.tryConsume(shareToken, LocalDateTime.now());
        if (affected == 0) {
            // 并发争用 / 已过期 / 已撤销 / 超限；重读 + 区分
            SysFileShare latest = mapper.selectByToken(shareToken);
            if (latest == null) {
                throw new FileException(FileException.Code.SHARE_NOT_FOUND, "分享不存在: " + shareToken);
            }
            assertTenant(latest);
            assertNotRevokedOrExpired(latest);
            // 走到这里说明是超限
            throw new FileException(FileException.Code.SHARE_DOWNLOAD_LIMIT_EXCEEDED,
                    "分享下载次数已达上限");
        }
        // 签名 URL 自带"密码 + 次数 + 过期"的链接级权限；FileService.signedDownloadUrl 内部仍走 FilePermissionEvaluator 校验
        String url = fileService.signedDownloadUrl(entity.getFileId(), props.getSignedUrlTtl());
        Instant exp = Instant.now().plus(props.getSignedUrlTtl());
        return new FileShareConsumeResult(url, exp);
    }

    @Override
    public List<FileShareInfo> listShares(String fileId) {
        Long tenantId = TenantContext.require();
        // 列前先确保文件归属（不存在抛 FILE_NOT_FOUND；跨租户由 MP TenantLine 兜底）
        FileMetadata file = fileService.metadata(fileId);
        return mapper.selectAliveByFileId(tenantId, fileId).stream()
                .map(e -> toInfo(e, file))
                .toList();
    }

    @Override
    public FileShareInfo getPublicShare(String shareToken) {
        SysFileShare entity = loadAndValidatePublic(shareToken);
        FileMetadata file = fileService.metadata(entity.getFileId());
        return toInfo(entity, file);
    }

    @Override
    public FileShareConsumeResult consumePublicShare(String shareToken, String passwordPlain) {
        SysFileShare entity = loadAndValidatePublic(shareToken);
        if (entity.getPasswordHash() != null) {
            if (passwordPlain == null || passwordPlain.isBlank()) {
                throw new FileException(FileException.Code.SHARE_PASSWORD_REQUIRED, "该分享需要密码");
            }
            if (!passwordHasher.verify(passwordPlain, entity.getPasswordHash())) {
                throw new FileException(FileException.Code.SHARE_PASSWORD_WRONG, "密码错误");
            }
        }
        int affected = mapper.tryConsume(shareToken, LocalDateTime.now());
        if (affected == 0) {
            // 并发争用 / 已过期 / 已撤销 / 超限；重读 + 区分（公开路径不做租户校验）
            SysFileShare latest = mapper.selectByToken(shareToken);
            if (latest == null) {
                throw new FileException(FileException.Code.SHARE_NOT_FOUND, "分享不存在: " + shareToken);
            }
            assertNotRevokedOrExpired(latest);
            throw new FileException(FileException.Code.SHARE_DOWNLOAD_LIMIT_EXCEEDED,
                    "分享下载次数已达上限");
        }
        String url = fileService.signedDownloadUrl(entity.getFileId(), props.getSignedUrlTtl());
        Instant exp = Instant.now().plus(props.getSignedUrlTtl());
        return new FileShareConsumeResult(url, exp);
    }

    @Override
    @Transactional
    public void revokeShare(String shareToken) {
        SysFileShare entity = mapper.selectByToken(shareToken);
        if (entity == null) {
            throw new FileException(FileException.Code.SHARE_NOT_FOUND, "分享不存在: " + shareToken);
        }
        assertTenant(entity);
        entity.setStatus("revoked");
        entity.setUpdatedAt(LocalDateTime.now());
        UserPrincipal principal = UserContext.get();
        if (principal != null) {
            entity.setUpdatedBy(principal.userId());
        }
        mapper.updateById(entity);
        // @TableLogic 自动 set deleted_at = now()
        mapper.deleteById(entity.getId());
    }

    // ============================== helpers ==============================

    private SysFileShare loadAndValidate(String shareToken) {
        SysFileShare entity = mapper.selectByToken(shareToken);
        if (entity == null) {
            throw new FileException(FileException.Code.SHARE_NOT_FOUND, "分享不存在: " + shareToken);
        }
        assertTenant(entity);
        assertNotRevokedOrExpired(entity);
        return entity;
    }

    /**
     * 公开（免登录）路径加载并校验：token 全局唯一、自携租户，<b>不做租户一致校验</b>。
     * 调用入口（{@code FileSharePublicController}）须 {@code @CrossTenantQuery} 绕过 MP TenantLine，
     * 否则 {@code mapper.selectByToken} 会因 {@link TenantContext#require()} 抛 IllegalStateException。
     */
    private SysFileShare loadAndValidatePublic(String shareToken) {
        SysFileShare entity = mapper.selectByToken(shareToken);
        if (entity == null) {
            throw new FileException(FileException.Code.SHARE_NOT_FOUND, "分享不存在: " + shareToken);
        }
        assertNotRevokedOrExpired(entity);
        return entity;
    }

    private void assertTenant(SysFileShare entity) {
        Long tenantId = TenantContext.require();
        if (!tenantId.equals(entity.getTenantId())) {
            // 不暴露存在性
            throw new FileException(FileException.Code.SHARE_NOT_FOUND,
                    "分享不存在: " + entity.getShareToken());
        }
    }

    private void assertNotRevokedOrExpired(SysFileShare entity) {
        if ("revoked".equals(entity.getStatus())) {
            throw new FileException(FileException.Code.SHARE_REVOKED, "分享已撤销");
        }
        if (entity.getExpiresAt() != null && entity.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new FileException(FileException.Code.SHARE_EXPIRED, "分享已过期");
        }
    }

    private String generateShareToken() {
        long id = IdWorker.getId();
        return base62(id);
    }

    private static String base62(long value) {
        final String alphabet = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        StringBuilder sb = new StringBuilder();
        long v = value;
        if (v == 0) return "0";
        while (v > 0) {
            sb.append(alphabet.charAt((int) (v % 62)));
            v /= 62;
        }
        return sb.reverse().toString();
    }

    private FileShareInfo toInfo(SysFileShare e, FileMetadata file) {
        Integer remaining = null;
        if (e.getMaxDownloads() != null) {
            int dc = Optional.ofNullable(e.getDownloadCount()).orElse(0);
            remaining = Math.max(0, e.getMaxDownloads() - dc);
        }
        return new FileShareInfo(
                e.getShareToken(),
                e.getFileId(),
                file == null ? null : file.fileName(),
                file == null ? null : file.mimeType(),
                file == null ? null : file.sizeBytes(),
                e.getExpiresAt(),
                e.getPasswordHash() != null,
                e.getMaxDownloads(),
                Optional.ofNullable(e.getDownloadCount()).orElse(0),
                remaining,
                e.getStatus(),
                e.getCreatedAt(),
                e.getCreatedBy());
    }
}
