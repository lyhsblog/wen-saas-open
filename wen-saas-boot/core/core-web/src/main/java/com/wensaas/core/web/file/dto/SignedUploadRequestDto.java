package com.wensaas.core.web.file.dto;

import com.wensaas.core.api.file.FileVisibility;
import com.wensaas.core.api.file.SignedUploadRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 申请签名上传 URL 的 REST 入参。复刻 {@link SignedUploadRequest} 字段供 Jackson 反序列化 + @Valid 约束。
 */
public record SignedUploadRequestDto(
        @NotBlank String fileName,
        String mimeType,
        @PositiveOrZero long sizeBytes,
        @NotNull FileVisibility visibility,
        String ownerModule,
        String ownerRefType,
        Long ownerRefId,
        String folderPath) {

    public SignedUploadRequest toRequest() {
        return new SignedUploadRequest(fileName, mimeType, sizeBytes, visibility,
                ownerModule, ownerRefType, ownerRefId, folderPath);
    }
}
