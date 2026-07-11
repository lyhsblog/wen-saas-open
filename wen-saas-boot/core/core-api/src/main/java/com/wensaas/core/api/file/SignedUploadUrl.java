package com.wensaas.core.api.file;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * 签名上传 URL。客户端按 {@link #method} + {@link #headers} 直接请求 {@link #url} 上传文件。
 *
 * @param fileId    底座生成的全局 fileId（确认上传时回传此 ID）
 * @param url       签名 URL（5 分钟内有效）
 * @param method    HTTP 方法（OSS 通常是 {@code PUT}）
 * @param headers   必须随请求一起发送的头（如 {@code Content-Type}）；客户端不可修改
 * @param expiresAt 过期时间点
 */
public record SignedUploadUrl(
        String fileId,
        String url,
        String method,
        Map<String, String> headers,
        Instant expiresAt) {

    public SignedUploadUrl {
        Objects.requireNonNull(fileId, "fileId");
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(expiresAt, "expiresAt");
        headers = headers == null ? Map.of() : Map.copyOf(headers);
    }
}
