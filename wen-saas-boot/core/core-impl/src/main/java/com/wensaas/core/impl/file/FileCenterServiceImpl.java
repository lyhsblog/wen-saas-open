package com.wensaas.core.impl.file;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wensaas.core.api.audit.AuditObjects;
import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.auth.user.UserPrincipal;
import com.wensaas.core.api.file.FileService;
import com.wensaas.core.api.file.FileStorage;
import com.wensaas.core.api.file.FileUploadRequest;
import com.wensaas.core.api.file.FileUploadResult;
import com.wensaas.core.api.file.FileVisibility;
import com.wensaas.core.api.file.center.FileCenterException;
import com.wensaas.core.api.file.center.FileCenterItem;
import com.wensaas.core.api.file.center.FileCenterListView;
import com.wensaas.core.api.file.center.FileCenterService;
import com.wensaas.core.api.file.center.FolderView;
import com.wensaas.core.api.file.center.QuotaView;
import com.wensaas.core.api.file.center.RecycleItem;
import com.wensaas.core.api.file.center.RecycleListView;
import com.wensaas.core.api.tenant.TenantContext;
import com.wensaas.core.api.tenant.TenantQueryService;
import com.wensaas.core.api.tenant.TenantQueryService.TenantInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.wensaas.core.impl.file.storage.StorageBackendResolver;

/**
 * {@link FileCenterService} 实现。
 *
 * <p>存储 / 签名 URL 委托 {@link FileService}；本类只补「文件中心」语义层：
 * 三空间、文件夹树、回收站、配额。回收站相关 SQL 在 Mapper XML 显式控制 {@code deleted_at}
 * 以绕过 @TableLogic。
 */
public class FileCenterServiceImpl implements FileCenterService {

    private static final Logger log = LoggerFactory.getLogger(FileCenterServiceImpl.class);
    private static final int MAX_DEPTH = 5;
    private static final int RECYCLE_RETENTION_DAYS = 30;

    private final SysFileMapper fileMapper;
    private final SysFileFolderMapper folderMapper;
    private final TenantQueryService tenantQuery;
    private final FileService fileService;
    private final StorageBackendResolver resolver;
    private final FileDownloadUrlCache urlCache;
    private final FileProperties props;

    public FileCenterServiceImpl(SysFileMapper fileMapper,
                                 SysFileFolderMapper folderMapper,
                                 TenantQueryService tenantQuery,
                                 FileService fileService,
                                 StorageBackendResolver resolver,
                                 FileDownloadUrlCache urlCache,
                                 FileProperties props) {
        this.fileMapper = fileMapper;
        this.folderMapper = folderMapper;
        this.tenantQuery = tenantQuery;
        this.fileService = fileService;
        this.resolver = resolver;
        this.urlCache = urlCache;
        this.props = props;
    }

    // ===================== 文件夹 =====================

    @Override
    public FolderView createFolder(String space, Long parentId, String name) {
        validateSpace(space);
        requireName(name);
        Long ownerScope = ownerScope(space);
        int depth = 1;
        if (parentId != null) {
            SysFileFolder parent = requireActiveFolder(parentId, space);
            depth = parent.getDepth() + 1;
            if (depth > MAX_DEPTH) {
                throw new FileCenterException(FileCenterException.Code.FOLDER_DEPTH_EXCEEDED,
                        "文件夹层级不能超过 " + MAX_DEPTH + " 层");
            }
        }
        // 新建文件夹重名不再拒绝，改为对齐文件现状自动加 (2)(3) 后缀（T5-2）。
        List<String> siblingNames = folderMapper.selectChildren(space, ownerScope, parentId)
                .stream().map(SysFileFolder::getName).toList();
        String finalName = dedupName(name.trim(), siblingNames);

        UserPrincipal principal = UserContext.get();
        Long userId = principal == null ? null : principal.userId();
        LocalDateTime now = LocalDateTime.now();
        SysFileFolder f = new SysFileFolder();
        f.setId(IdWorker.getId());
        f.setTenantId(TenantContext.require());
        f.setSpace(space);
        f.setParentId(parentId);
        f.setName(finalName);
        f.setDepth(depth);
        f.setOwnerId(SPACE_MINE.equals(space) ? userId : null);
        f.setCreatedAt(now);
        f.setCreatedBy(userId);
        f.setUpdatedAt(now);
        f.setUpdatedBy(userId);
        folderMapper.insert(f);
        return toFolderView(f);
    }

    @Override
    public FolderView renameFolder(Long folderId, String newName) {
        requireName(newName);
        SysFileFolder f = folderMapper.selectById(folderId);
        if (f == null) {
            throw new FileCenterException(FileCenterException.Code.FOLDER_NOT_FOUND, "文件夹不存在: " + folderId);
        }
        // ② 审计对象名：加载后即登记「旧名/新名」（业务前，失败也留意图）
        AuditObjects.add(null, String.valueOf(folderId), f.getName(), newName.trim());
        ensureNoSiblingName(f.getSpace(), f.getOwnerId(), f.getParentId(), newName, folderId);
        f.setName(newName.trim());
        f.setUpdatedAt(LocalDateTime.now());
        f.setUpdatedBy(currentUserId());
        folderMapper.updateById(f);
        return toFolderView(f);
    }

    @Override
    @Transactional
    public void moveFolder(Long folderId, Long newParentId) {
        SysFileFolder f = folderMapper.selectById(folderId);
        if (f == null) {
            throw new FileCenterException(FileCenterException.Code.FOLDER_NOT_FOUND, "文件夹不存在: " + folderId);
        }
        List<Long> subtreeIds = folderMapper.selectSubtreeIds(folderId, TenantContext.require());
        int newDepth = 1;
        if (newParentId != null) {
            if (newParentId.equals(folderId) || subtreeIds.contains(newParentId)) {
                throw new FileCenterException(FileCenterException.Code.FOLDER_MOVE_CYCLE, "不能移动到自身或子目录下");
            }
            SysFileFolder newParent = requireActiveFolder(newParentId, f.getSpace());
            newDepth = newParent.getDepth() + 1;
        }
        // 子树相对高度校验
        List<SysFileFolder> subtree = subtreeIds.isEmpty() ? List.of() : folderMapper.selectBatchIds(subtreeIds);
        int maxDepth = subtree.stream().mapToInt(SysFileFolder::getDepth).max().orElse(f.getDepth());
        int height = maxDepth - f.getDepth();
        if (newDepth + height > MAX_DEPTH) {
            throw new FileCenterException(FileCenterException.Code.FOLDER_DEPTH_EXCEEDED,
                    "移动后文件夹层级会超过 " + MAX_DEPTH + " 层");
        }
        ensureNoSiblingName(f.getSpace(), f.getOwnerId(), newParentId, f.getName(), folderId);

        int delta = newDepth - f.getDepth();
        if (delta != 0) {
            List<Long> descendants = new ArrayList<>(subtreeIds);
            descendants.remove(folderId);
            if (!descendants.isEmpty()) {
                folderMapper.shiftDepthByIds(descendants, delta);
            }
        }
        f.setParentId(newParentId);
        f.setDepth(newDepth);
        f.setUpdatedAt(LocalDateTime.now());
        f.setUpdatedBy(currentUserId());
        folderMapper.updateById(f);
    }

    @Override
    @Transactional
    public void deleteFolder(Long folderId) {
        SysFileFolder f = folderMapper.selectById(folderId);
        if (f == null) {
            throw new FileCenterException(FileCenterException.Code.FOLDER_NOT_FOUND, "文件夹不存在: " + folderId);
        }
        // ② 审计对象名：登记文件夹名而非裸 id
        AuditObjects.add(null, String.valueOf(folderId), f.getName());
        List<Long> subtreeIds = folderMapper.selectSubtreeIds(folderId, TenantContext.require());
        if (!subtreeIds.isEmpty()) {
            List<Long> fileIds = fileMapper.selectActiveFileIdsByFolderIds(subtreeIds);
            folderMapper.softDeleteByIds(subtreeIds);
            if (!fileIds.isEmpty()) {
                fileMapper.softDeleteByIds(fileIds);
            }
        }
    }

    // ===================== 列表 / 上传 =====================

    @Override
    public FileCenterListView list(String space, Long folderId, String keyword, int page, int size) {
        validateSpace(space);
        Long ownerScope = ownerScope(space);
        String kw = keyword == null ? null : keyword.trim();

        List<SysFileFolder> children = folderMapper.selectChildren(space, ownerScope, folderId);
        List<FolderView> folders = new ArrayList<>();
        for (SysFileFolder c : children) {
            if (kw == null || kw.isEmpty() || c.getName().toLowerCase().contains(kw.toLowerCase())) {
                folders.add(toFolderView(c));
            }
        }

        IPage<SysFile> filePage = fileMapper.selectCenterPage(
                new Page<>(page, size), space, ownerScope, folderId,
                kw == null || kw.isEmpty() ? null : kw);
        List<FileCenterItem> files = filePage.getRecords().stream().map(this::toFileItem).toList();

        return new FileCenterListView(folders, files, filePage.getTotal(), page, size, buildBreadcrumb(folderId));
    }

    @Override
    public FileUploadResult upload(String space, Long folderId, String fileName, String mimeType,
                                   long sizeBytes, InputStream content) {
        validateSpace(space);
        if (folderId != null) {
            requireActiveFolder(folderId, space);
        }
        ensureWithinQuota(sizeBytes);

        Long ownerScope = ownerScope(space);
        List<String> existing = fileMapper.selectFileNamesInFolder(space, ownerScope, folderId);
        String finalName = dedupName(fileName, existing);

        FileUploadRequest req = new FileUploadRequest(finalName, mimeType, sizeBytes, content,
                FileVisibility.FILE_CENTER, null, null, null, null);
        FileUploadResult result = fileService.uploadDirect(req);
        fileMapper.bindCenterAttrs(result.fileId(), space, folderId);
        return new FileUploadResult(result.fileId(), finalName, result.sizeBytes(),
                result.mimeType(), result.backendType());
    }

    @Override
    public void renameFile(String fileId, String newName) {
        requireName(newName);
        SysFile file = fileMapper.selectByFileId(fileId);
        if (file == null) {
            throw new FileCenterException(FileCenterException.Code.FILE_NOT_FOUND, "文件不存在: " + fileId);
        }
        // ② 审计对象名：旧名/新名
        AuditObjects.add(null, fileId, file.getFileName(), newName.trim());
        fileMapper.updateCenterAttrs(fileId, file.getSpace(), file.getFolderId(), newName.trim());
    }

    @Override
    public void moveFile(String fileId, String targetSpace, Long targetFolderId) {
        validateSpace(targetSpace);
        SysFile file = fileMapper.selectByFileId(fileId);
        if (file == null) {
            throw new FileCenterException(FileCenterException.Code.FILE_NOT_FOUND, "文件不存在: " + fileId);
        }
        if (targetFolderId != null) {
            requireActiveFolder(targetFolderId, targetSpace);
        }
        Long ownerScope = ownerScope(targetSpace);
        List<String> existing = fileMapper.selectFileNamesInFolder(targetSpace, ownerScope, targetFolderId);
        String finalName = dedupName(file.getFileName(), existing);
        fileMapper.updateCenterAttrs(fileId, targetSpace, targetFolderId, finalName);
    }

    @Override
    public void deleteFile(String fileId) {
        SysFile file = fileMapper.selectByFileId(fileId);
        if (file == null) {
            throw new FileCenterException(FileCenterException.Code.FILE_NOT_FOUND, "文件不存在: " + fileId);
        }
        // ② 审计对象名：登记文件名而非裸 id
        AuditObjects.add(null, fileId, file.getFileName());
        // 复用 FileService.delete：@TableLogic 软删 + Redis URL 缓存 evict
        fileService.delete(fileId);
    }

    // ===================== 回收站 =====================

    @Override
    public RecycleListView listRecycle(String space, int page, int size) {
        validateSpace(space);
        Long ownerScope = ownerScope(space);
        List<RecycleItem> items = new ArrayList<>();
        for (SysFile f : fileMapper.selectRecycledFiles(space, ownerScope)) {
            items.add(new RecycleItem(RecycleItem.TYPE_FILE, f.getFileId(), f.getFileName(),
                    f.getSizeBytes() == null ? 0L : f.getSizeBytes(),
                    f.getDeletedAt(), purgeAt(f.getDeletedAt())));
        }
        for (SysFileFolder d : folderMapper.selectRecycledRoots(space, ownerScope)) {
            items.add(new RecycleItem(RecycleItem.TYPE_FOLDER, String.valueOf(d.getId()), d.getName(),
                    0L, d.getDeletedAt(), purgeAt(d.getDeletedAt())));
        }
        items.sort((a, b) -> {
            if (a.deletedAt() == null || b.deletedAt() == null) return 0;
            return b.deletedAt().compareTo(a.deletedAt());
        });
        long total = items.size();
        int from = Math.max(0, (page - 1) * size);
        int to = Math.min(items.size(), from + size);
        List<RecycleItem> pageItems = from >= items.size() ? List.of() : items.subList(from, to);
        return new RecycleListView(new ArrayList<>(pageItems), total, page, size);
    }

    @Override
    @Transactional
    public void restore(List<String> fileIds, List<Long> folderIds) {
        if (fileIds != null) {
            for (String fileId : fileIds) {
                SysFile file = fileMapper.selectByFileIdIncludeDeleted(fileId);
                if (file == null || file.getDeletedAt() == null) {
                    throw new FileCenterException(FileCenterException.Code.NOT_RECYCLED, "文件不在回收站: " + fileId);
                }
                Long targetFolder = folderAliveOrNull(file.getFolderId());
                fileMapper.restoreFile(file.getId(), targetFolder);
                urlCache.evict(fileId);
            }
        }
        if (folderIds != null) {
            for (Long folderId : folderIds) {
                SysFileFolder f = folderMapper.selectByIdIncludeDeleted(folderId);
                if (f == null || f.getDeletedAt() == null) {
                    throw new FileCenterException(FileCenterException.Code.NOT_RECYCLED, "文件夹不在回收站: " + folderId);
                }
                List<Long> subtree = folderMapper.selectDeletedSubtreeIds(folderId, TenantContext.require());
                if (!subtree.isEmpty()) {
                    folderMapper.restoreByIds(subtree);
                    fileMapper.restoreFilesByFolderIds(subtree);
                }
                // 父目录已不存在 → 改挂 space 根
                folderMapper.reattachToRoot(folderId);
            }
        }
    }

    @Override
    @Transactional
    public void purge(List<String> fileIds, List<Long> folderIds) {
        if (fileIds != null) {
            for (String fileId : fileIds) {
                SysFile file = fileMapper.selectByFileIdIncludeDeleted(fileId);
                if (file != null) {
                    // ② 审计对象名：逐个登记彻底删除的文件名（多对象 object_refs）
                    AuditObjects.add(null, file.getFileId(), file.getFileName());
                    purgeFilePhysical(file);
                }
            }
        }
        if (folderIds != null) {
            for (Long folderId : folderIds) {
                List<Long> subtree = folderMapper.selectDeletedSubtreeIds(folderId, TenantContext.require());
                if (subtree.isEmpty()) {
                    continue;
                }
                for (SysFile file : fileMapper.selectDeletedFilesByFolderIds(subtree)) {
                    AuditObjects.add(null, file.getFileId(), file.getFileName());
                    purgeFilePhysical(file);
                }
                folderMapper.physicalDeleteByIds(subtree);
            }
        }
    }

    // ===================== 配额 =====================

    @Override
    public QuotaView quota() {
        long used = fileMapper.sumActiveSize(TenantContext.require());
        return new QuotaView(used, resolveCapBytes());
    }

    // ===================== 内部辅助 =====================

    private void purgeFilePhysical(SysFile file) {
        try {
            FileStorage storage = resolver.resolveByConfigId(file.getBackendConfigId());
            storage.delete(file.getBackendObjectKey());
        } catch (RuntimeException e) {
            log.warn("[FileCenter] 彻底删除后端对象失败（忽略，继续删元数据） fileId={}, key={}: {}",
                    file.getFileId(), file.getBackendObjectKey(), e.toString());
        }
        fileMapper.physicalDelete(file.getId());
        urlCache.evict(file.getFileId());
    }

    private Long resolveCapBytes() {
        Long cap = null;
        TenantInfo t = tenantQuery.tenantById(TenantContext.require());
        if (t != null) {
            cap = t.storageCap();
        }
        return cap != null ? cap : props.getDefaultQuota().toBytes();
    }

    private void ensureWithinQuota(long incomingBytes) {
        Long cap = resolveCapBytes();
        if (cap == null) {
            return;
        }
        long used = fileMapper.sumActiveSize(TenantContext.require());
        if (used + incomingBytes > cap) {
            throw new FileCenterException(FileCenterException.Code.QUOTA_EXCEEDED,
                    "超出容量配额：已用 " + used + " + 本次 " + incomingBytes + " > 上限 " + cap);
        }
    }

    /** 返回文件夹（活跃）若存在，否则 null（用于还原时判断父是否仍在）。 */
    private Long folderAliveOrNull(Long folderId) {
        if (folderId == null) {
            return null;
        }
        return folderMapper.selectById(folderId) != null ? folderId : null;
    }

    private SysFileFolder requireActiveFolder(Long folderId, String space) {
        SysFileFolder f = folderMapper.selectById(folderId);
        if (f == null || !space.equals(f.getSpace())) {
            throw new FileCenterException(FileCenterException.Code.FOLDER_NOT_FOUND, "文件夹不存在: " + folderId);
        }
        return f;
    }

    private void ensureNoSiblingName(String space, Long ownerId, Long parentId, String name, Long excludeId) {
        String target = name.trim();
        for (SysFileFolder sib : folderMapper.selectChildren(space, ownerId, parentId)) {
            if (sib.getName().equals(target) && !sib.getId().equals(excludeId)) {
                throw new FileCenterException(FileCenterException.Code.FOLDER_NAME_DUP, "同级已存在同名文件夹: " + target);
            }
        }
    }

    private List<FolderView> buildBreadcrumb(Long folderId) {
        List<FolderView> crumb = new ArrayList<>();
        Long cur = folderId;
        int guard = 0;
        while (cur != null && guard++ < MAX_DEPTH + 1) {
            SysFileFolder f = folderMapper.selectById(cur);
            if (f == null) {
                break;
            }
            crumb.add(toFolderView(f));
            cur = f.getParentId();
        }
        Collections.reverse(crumb);
        return crumb;
    }

    /** 重名自动加 (2)(3) 后缀。 */
    static String dedupName(String name, List<String> existing) {
        Set<String> taken = new HashSet<>(existing);
        if (!taken.contains(name)) {
            return name;
        }
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        for (int n = 2; ; n++) {
            String candidate = base + " (" + n + ")" + ext;
            if (!taken.contains(candidate)) {
                return candidate;
            }
        }
    }

    private LocalDateTime purgeAt(LocalDateTime deletedAt) {
        return deletedAt == null ? null : deletedAt.plusDays(RECYCLE_RETENTION_DAYS);
    }

    private Long ownerScope(String space) {
        return SPACE_MINE.equals(space) ? currentUserId() : null;
    }

    private Long currentUserId() {
        UserPrincipal p = UserContext.get();
        return p == null ? null : p.userId();
    }

    private void validateSpace(String space) {
        if (!SPACE_MINE.equals(space) && !SPACE_SHARED.equals(space)) {
            throw new FileCenterException(FileCenterException.Code.INVALID_ARGUMENT, "未知 space: " + space);
        }
    }

    private void requireName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new FileCenterException(FileCenterException.Code.INVALID_ARGUMENT, "名称不能为空");
        }
    }

    private FolderView toFolderView(SysFileFolder f) {
        return new FolderView(f.getId(), f.getSpace(), f.getParentId(), f.getName(), f.getDepth());
    }

    private FileCenterItem toFileItem(SysFile f) {
        return new FileCenterItem(f.getFileId(), f.getFileName(), f.getMimeType(),
                f.getSizeBytes() == null ? 0L : f.getSizeBytes(),
                f.getFolderId(), f.getUploadedBy(), f.getUploadedAt());
    }
}
