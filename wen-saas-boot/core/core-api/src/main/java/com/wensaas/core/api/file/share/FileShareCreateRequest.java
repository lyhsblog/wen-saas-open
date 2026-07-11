package com.wensaas.core.api.file.share;

import java.time.Duration;

/**
 * 创建分享请求。
 *
 * @param fileId         被分享的 sys_file.file_id
 * @param expiresAfter   有效期（产品档位 P0 = 7d / 30d / 永久）；null = 永久
 * @param passwordPlain  4-6 位访问密码；null/blank = 无密码
 * @param maxDownloads   最大下载次数；null = 不限
 */
public record FileShareCreateRequest(
        String fileId,
        Duration expiresAfter,
        String passwordPlain,
        Integer maxDownloads) {

    public FileShareCreateRequest {
        if (fileId == null || fileId.isBlank()) {
            throw new IllegalArgumentException("fileId 不能为空");
        }
        if (passwordPlain != null && !passwordPlain.isBlank()) {
            int len = passwordPlain.length();
            if (len < 4 || len > 6) {
                throw new IllegalArgumentException("密码长度必须 4-6 位");
            }
        }
        if (maxDownloads != null && maxDownloads <= 0) {
            throw new IllegalArgumentException("maxDownloads 必须为正整数");
        }
    }
}
