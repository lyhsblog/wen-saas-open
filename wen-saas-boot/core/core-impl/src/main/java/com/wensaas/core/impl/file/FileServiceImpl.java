package com.wensaas.core.impl.file;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.auth.user.UserPrincipal;
import com.wensaas.core.api.file.FileBackendType;
import com.wensaas.core.api.file.FileException;
import com.wensaas.core.api.file.FileMetadata;
import com.wensaas.core.api.file.FileService;
import com.wensaas.core.api.file.FileStorage;
import com.wensaas.core.api.file.FileUploadRequest;
import com.wensaas.core.api.file.FileUploadResult;
import com.wensaas.core.api.file.FileVisibility;
import com.wensaas.core.api.file.SignedUploadRequest;
import com.wensaas.core.api.file.SignedUploadUrl;
import com.wensaas.core.api.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import com.wensaas.core.impl.file.storage.StorageBackendResolver;

/**
 * {@link FileService} 实现：直传 / 签名直传（requestSignedUpload → confirmUpload 落元数据）、
 * 签名下载 URL、删除与 owner 维度查询；存储后端由 StorageBackendResolver 按配置路由。
 */
public class FileServiceImpl implements FileService {

    private static final Logger log = LoggerFactory.getLogger(FileServiceImpl.class);
    private static final DateTimeFormatter YYYYMM = DateTimeFormatter.ofPattern("yyyyMM");

    private final SysFileMapper fileMapper;
    private final StorageBackendResolver resolver;
    private final FileDownloadUrlCache urlCache;
    private final FilePermissionEvaluator permissionEvaluator;
    private final FileProperties props;

    public FileServiceImpl(SysFileMapper fileMapper,
                           StorageBackendResolver resolver,
                           FileDownloadUrlCache urlCache,
                           FilePermissionEvaluator permissionEvaluator,
                           FileProperties props) {
        this.fileMapper = fileMapper;
        this.resolver = resolver;
        this.urlCache = urlCache;
        this.permissionEvaluator = permissionEvaluator;
        this.props = props;
    }

    @Override
    public FileUploadResult uploadDirect(FileUploadRequest request) {
        ensureWithinMaxSize(request.sizeBytes());
        Long tenantId = TenantContext.require();
        StorageBackendResolver.ResolveResult resolve = resolver.resolveActive(tenantId);
        FileStorage storage = resolve.storage();

        String fileId = generateFileId();
        String objectKey = buildObjectKey(resolve.pathPrefix(), tenantId, fileId);
        SysFile entity = newEntity(tenantId, fileId, objectKey, storage.backendType(),
                resolve.backendConfigId(), request, "active");

        storage.upload(objectKey, request.content(), request.sizeBytes(), request.mimeType());
        fileMapper.insert(entity);

        return new FileUploadResult(fileId, request.fileName(), request.sizeBytes(),
                request.mimeType(), storage.backendType());
    }

    @Override
    public SignedUploadUrl requestSignedUpload(SignedUploadRequest request) {
        ensureWithinMaxSize(request.sizeBytes());
        Long tenantId = TenantContext.require();
        StorageBackendResolver.ResolveResult resolve = resolver.resolveActive(tenantId);
        FileStorage storage = resolve.storage();

        String fileId = generateFileId();
        String objectKey = buildObjectKey(resolve.pathPrefix(), tenantId, fileId);

        SysFile entity = newEntity(tenantId, fileId, objectKey, storage.backendType(),
                resolve.backendConfigId(),
                new FileUploadRequest(request.fileName(), request.mimeType(), request.sizeBytes(),
                        java.io.InputStream.nullInputStream(),
                        request.visibility(), request.ownerModule(),
                        request.ownerRefType(), request.ownerRefId(), request.folderPath()),
                "uploading");
        fileMapper.insert(entity);

        return storage.signedUploadUrl(objectKey, request.mimeType(), request.sizeBytes(),
                props.getSignedUrlTtl());
    }

    @Override
    public FileUploadResult confirmUpload(String fileId) {
        SysFile entity = fileMapper.selectByFileId(fileId);
        if (entity == null) {
            throw new FileException(FileException.Code.FILE_NOT_FOUND,
                    "fileId 不存在或已删除: " + fileId);
        }
        if (!"uploading".equals(entity.getUploadState())) {
            throw new FileException(FileException.Code.UPLOAD_NOT_CONFIRMABLE,
                    "fileId=" + fileId + " 当前 state=" + entity.getUploadState() + "，不可确认");
        }
        FileStorage storage = resolver.resolveByConfigId(entity.getBackendConfigId());
        FileStorage.BackendObjectInfo info = storage.metadata(entity.getBackendObjectKey());
        if (info == null) {
            throw new FileException(FileException.Code.UPLOAD_NOT_CONFIRMABLE,
                    "后端找不到 objectKey: " + entity.getBackendObjectKey());
        }
        entity.setUploadState("active");
        entity.setSizeBytes(info.sizeBytes());
        if (entity.getMimeType() == null && info.mimeType() != null) {
            entity.setMimeType(info.mimeType());
        }
        entity.setUpdatedAt(LocalDateTime.now());
        fileMapper.updateById(entity);

        return new FileUploadResult(fileId, entity.getFileName(), info.sizeBytes(),
                entity.getMimeType(), FileBackendType.fromCode(entity.getBackendType()));
    }

    @Override
    public String signedDownloadUrl(String fileId, Duration validity) {
        Optional<String> cached = urlCache.get(fileId);
        if (cached.isPresent()) {
            return cached.get();
        }
        SysFile entity = fileMapper.selectByFileId(fileId);
        if (entity == null) {
            throw new FileException(FileException.Code.FILE_NOT_FOUND,
                    "fileId 不存在或已删除: " + fileId);
        }
        FileMetadata metadata = toMetadata(entity);
        Long currentUserId = Optional.ofNullable(UserContext.get())
                .map(UserPrincipal::userId).orElse(null);
        if (!permissionEvaluator.canDownload(metadata, currentUserId)) {
            throw new FileException(FileException.Code.DOWNLOAD_FORBIDDEN,
                    "当前用户无权下载: " + fileId);
        }
        FileStorage storage = resolver.resolveByConfigId(entity.getBackendConfigId());
        Duration actual = validity == null ? props.getSignedUrlTtl() : validity;
        String url = storage.signedDownloadUrl(entity.getBackendObjectKey(), actual);
        urlCache.put(fileId, url);
        return url;
    }

    @Override
    public byte[] readContent(String fileId) {
        SysFile entity = fileMapper.selectByFileId(fileId);
        if (entity == null) {
            throw new FileException(FileException.Code.FILE_NOT_FOUND,
                    "fileId 不存在或已删除: " + fileId);
        }
        FileStorage storage = resolver.resolveByConfigId(entity.getBackendConfigId());
        try (java.io.InputStream in = storage.download(entity.getBackendObjectKey())) {
            return in.readAllBytes();
        } catch (java.io.IOException e) {
            throw new FileException(FileException.Code.BACKEND_IO_ERROR,
                    "读取文件内容失败: " + fileId);
        }
    }

    @Override
    public void delete(String fileId) {
        SysFile entity = fileMapper.selectByFileId(fileId);
        if (entity == null) {
            throw new FileException(FileException.Code.FILE_NOT_FOUND,
                    "fileId 不存在或已删除: " + fileId);
        }
        // MP @TableLogic 自动 set deleted_at = now()
        fileMapper.deleteById(entity.getId());
        urlCache.evict(fileId);
    }

    @Override
    public FileMetadata metadata(String fileId) {
        SysFile entity = fileMapper.selectByFileId(fileId);
        if (entity == null) {
            throw new FileException(FileException.Code.FILE_NOT_FOUND,
                    "fileId 不存在或已删除: " + fileId);
        }
        return toMetadata(entity);
    }

    @Override
    public List<FileMetadata> listByOwner(String ownerModule, String ownerRefType, Long ownerRefId) {
        Long tenantId = TenantContext.require();
        return fileMapper.selectByOwner(tenantId, ownerModule, ownerRefType, ownerRefId).stream()
                .map(this::toMetadata)
                .collect(Collectors.toList());
    }

    private void ensureWithinMaxSize(long sizeBytes) {
        long max = props.getMaxSize().toBytes();
        if (sizeBytes > max) {
            throw new FileException(FileException.Code.SIZE_EXCEEDED,
                    "size " + sizeBytes + " 超过 max-size " + max);
        }
    }

    private String generateFileId() {
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

    private String buildObjectKey(String pathPrefix, long tenantId, String fileId) {
        String yearMonth = YearMonth.now().format(YYYYMM);
        StringBuilder sb = new StringBuilder();
        if (pathPrefix != null && !pathPrefix.isBlank()) {
            sb.append(pathPrefix.replaceAll("^/|/$", ""));
            sb.append('/');
        }
        sb.append("tenants/").append(tenantId).append('/').append(yearMonth).append('/').append(fileId);
        return sb.toString();
    }

    private SysFile newEntity(long tenantId, String fileId, String objectKey,
                              FileBackendType backendType, Long backendConfigId,
                              FileUploadRequest req, String uploadState) {
        UserPrincipal principal = UserContext.get();
        Long userId = principal == null ? null : principal.userId();
        LocalDateTime now = LocalDateTime.now();
        SysFile e = new SysFile();
        e.setTenantId(tenantId);
        e.setFileId(fileId);
        e.setFileName(req.fileName());
        e.setMimeType(req.mimeType());
        e.setSizeBytes(req.sizeBytes());
        e.setVisibility(req.visibility().code());
        e.setOwnerModule(req.ownerModule());
        e.setOwnerRefType(req.ownerRefType());
        e.setOwnerRefId(req.ownerRefId());
        e.setFolderPath(req.folderPath());
        e.setBackendType(backendType.code());
        e.setBackendConfigId(backendConfigId == 0L ? null : backendConfigId);
        e.setBackendObjectKey(objectKey);
        e.setUploadState(uploadState);
        e.setUploadedBy(userId);
        e.setUploadedAt(now);
        e.setCreatedAt(now);
        e.setCreatedBy(userId);
        e.setUpdatedAt(now);
        e.setUpdatedBy(userId);
        return e;
    }

    private FileMetadata toMetadata(SysFile e) {
        return new FileMetadata(
                e.getFileId(),
                e.getTenantId(),
                e.getFileName(),
                e.getMimeType(),
                e.getSizeBytes() == null ? 0L : e.getSizeBytes(),
                FileVisibility.fromCode(e.getVisibility()),
                e.getOwnerModule(),
                e.getOwnerRefType(),
                e.getOwnerRefId(),
                e.getFolderPath(),
                FileBackendType.fromCode(e.getBackendType()),
                e.getBackendConfigId(),
                e.getBackendObjectKey(),
                e.getUploadState(),
                e.getUploadedBy(),
                e.getUploadedAt(),
                e.getDeletedAt());
    }
}
