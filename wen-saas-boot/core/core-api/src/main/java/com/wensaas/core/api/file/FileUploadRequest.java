package com.wensaas.core.api.file;

import java.io.InputStream;
import java.util.Objects;

/**
 * 业务方调底座直接上传文件（{@code FileService.uploadDirect}）使用。
 *
 * <p>大文件 / 前端直传场景请改用 {@link SignedUploadRequest} + {@code FileService.requestSignedUpload}。
 *
 * @param fileName    文件名（含扩展名）
 * @param mimeType    可选 MIME 类型；为空时框架按扩展名兜底
 * @param sizeBytes   文件字节数
 * @param content     文件内容流；调用方负责释放（框架 try-with-resources 读取后不主动关闭原始流）
 * @param visibility  可见性
 * @param ownerModule 业务模块 code（仅 visibility=business-only 时强制非空）
 * @param ownerRefType 业务对象类型（如"客户"/"工单"）
 * @param ownerRefId  业务对象 ID
 * @param folderPath  文件中心目录路径（仅 visibility=file-center 时使用）
 */
public record FileUploadRequest(
        String fileName,
        String mimeType,
        long sizeBytes,
        InputStream content,
        FileVisibility visibility,
        String ownerModule,
        String ownerRefType,
        Long ownerRefId,
        String folderPath) {

    public FileUploadRequest {
        Objects.requireNonNull(fileName, "fileName");
        Objects.requireNonNull(content, "content");
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
