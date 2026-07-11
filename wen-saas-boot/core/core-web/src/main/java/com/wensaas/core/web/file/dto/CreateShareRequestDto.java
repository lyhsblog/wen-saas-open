package com.wensaas.core.web.file.dto;

import com.wensaas.core.api.file.share.FileShareCreateRequest;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Duration;

/**
 * 创建分享 REST 入参。
 *
 * @param expiresAfterSeconds  有效期（秒）；null = 永久
 * @param password             4-6 位明文密码（可选）
 * @param maxDownloads         最大下载次数（可选）
 */
public record CreateShareRequestDto(
        Long expiresAfterSeconds,
        @Size(min = 4, max = 6) String password,
        @Positive Integer maxDownloads) {

    public FileShareCreateRequest toRequest(String fileId) {
        Duration ttl = expiresAfterSeconds == null ? null : Duration.ofSeconds(expiresAfterSeconds);
        return new FileShareCreateRequest(fileId, ttl, password, maxDownloads);
    }
}
