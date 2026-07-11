package com.wensaas.core.api.file;

import java.util.Objects;

/**
 * 文件上传完成后返回给业务方的结果。
 *
 * @param fileId     全局唯一对外 ID（业务模块用此 ID 引用）
 * @param fileName   文件名
 * @param sizeBytes  实际字节数
 * @param mimeType   实际或解析后的 MIME 类型
 * @param backendType 实际写入的后端类型
 */
public record FileUploadResult(
        String fileId,
        String fileName,
        long sizeBytes,
        String mimeType,
        FileBackendType backendType) {

    public FileUploadResult {
        Objects.requireNonNull(fileId, "fileId");
        Objects.requireNonNull(backendType, "backendType");
    }
}
