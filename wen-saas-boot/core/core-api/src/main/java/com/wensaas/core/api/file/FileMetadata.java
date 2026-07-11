package com.wensaas.core.api.file;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 文件元数据视图。{@code FileService.metadata} / 文件中心列表 / 下载权限 SPI 等场景使用。
 *
 * <p>{@code accessKeySecret} / 后端凭证不会出现在此对象中。
 */
public record FileMetadata(
        String fileId,
        Long tenantId,
        String fileName,
        String mimeType,
        long sizeBytes,
        FileVisibility visibility,
        String ownerModule,
        String ownerRefType,
        Long ownerRefId,
        String folderPath,
        FileBackendType backendType,
        Long backendConfigId,
        String backendObjectKey,
        String uploadState,
        Long uploadedBy,
        LocalDateTime uploadedAt,
        LocalDateTime deletedAt) {

    public FileMetadata {
        Objects.requireNonNull(fileId, "fileId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(visibility, "visibility");
        Objects.requireNonNull(backendType, "backendType");
    }

    public boolean deleted() {
        return deletedAt != null;
    }

    public boolean active() {
        return "active".equals(uploadState);
    }
}
