package com.wensaas.core.api.file;

import java.util.Objects;

/**
 * 前端直传 OSS 申请签名 URL 时使用。
 *
 * <p>底座流程：校验 size → 解析当前租户 active 后端 → 生成 file_id + object_key →
 * 写 sys_file state=uploading → 调 {@code FileStorage.signedUploadUrl} → 返回。
 *
 * <p>客户端流程：拿到 {@link SignedUploadUrl} 后直接 PUT 到 OSS，
 * 然后调 {@code FileService.confirmUpload(fileId)} 让底座校验后端真实存在，把 state 改为 active。
 */
public record SignedUploadRequest(
        String fileName,
        String mimeType,
        long sizeBytes,
        FileVisibility visibility,
        String ownerModule,
        String ownerRefType,
        Long ownerRefId,
        String folderPath) {

    public SignedUploadRequest {
        Objects.requireNonNull(fileName, "fileName");
        Objects.requireNonNull(visibility, "visibility");
        if (sizeBytes < 0) {
            throw new IllegalArgumentException("sizeBytes 必须 >= 0: " + sizeBytes);
        }
        if (visibility == FileVisibility.BUSINESS_ONLY
                && (ownerModule == null || ownerModule.isBlank())) {
            throw new IllegalArgumentException("visibility=business-only 时 ownerModule 必填");
        }
    }
}
