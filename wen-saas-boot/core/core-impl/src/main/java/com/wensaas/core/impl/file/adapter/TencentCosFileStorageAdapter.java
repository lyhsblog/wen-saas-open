package com.wensaas.core.impl.file.adapter;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.http.HttpMethodName;
import com.qcloud.cos.model.GeneratePresignedUrlRequest;
import com.qcloud.cos.model.ObjectMetadata;
import com.wensaas.core.api.file.FileBackendType;
import com.wensaas.core.api.file.FileException;
import com.wensaas.core.api.file.FileStorage;
import com.wensaas.core.api.file.SignedUploadUrl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

/**
 * 腾讯云 COS 后端。
 *
 * <p>字段映射复用 {@code sys_storage_config} 通用列：{@code endpoint} 存 COS region
 * （如 {@code ap-guangzhou}），{@code access_key_id}/{@code access_key_secret} 存
 * SecretId/SecretKey。COSClient 是线程安全的重对象，{@code FileStorageFactory} 按
 * backendConfigId 缓存，切换 / 删除配置时通过 {@code shutdown()} 释放。
 */
public class TencentCosFileStorageAdapter implements FileStorage {

    private static final Logger log = LoggerFactory.getLogger(TencentCosFileStorageAdapter.class);

    private final COSClient cosClient;
    private final String bucket;
    private final String region;

    public TencentCosFileStorageAdapter(COSClient cosClient, String bucket, String region) {
        this.cosClient = cosClient;
        this.bucket = bucket;
        this.region = region;
        log.info("[FileStorage:TencentCos] ready, region={}, bucket={}", region, bucket);
    }

    @Override
    public FileBackendType backendType() {
        return FileBackendType.TENCENT_COS;
    }

    @Override
    public void upload(String objectKey, InputStream content, long contentLength, String mimeType) {
        ObjectMetadata meta = new ObjectMetadata();
        if (contentLength > 0) {
            meta.setContentLength(contentLength);
        }
        if (mimeType != null && !mimeType.isBlank()) {
            meta.setContentType(mimeType);
        }
        try {
            cosClient.putObject(bucket, objectKey, content, meta);
        } catch (CosClientException ex) {
            throw new FileException(FileException.Code.BACKEND_IO_ERROR,
                    "COS 写入失败: " + objectKey, ex);
        }
    }

    @Override
    public InputStream download(String objectKey) {
        try {
            return cosClient.getObject(bucket, objectKey).getObjectContent();
        } catch (CosClientException ex) {
            throw new FileException(FileException.Code.BACKEND_IO_ERROR,
                    "COS 读取失败: " + objectKey, ex);
        }
    }

    @Override
    public String signedDownloadUrl(String objectKey, Duration validity) {
        try {
            Date expiration = Date.from(Instant.now().plus(validity));
            return cosClient.generatePresignedUrl(bucket, objectKey, expiration).toString();
        } catch (CosClientException ex) {
            throw new FileException(FileException.Code.BACKEND_IO_ERROR,
                    "COS 签名 URL 生成失败: " + objectKey, ex);
        }
    }

    @Override
    public SignedUploadUrl signedUploadUrl(String objectKey, String mimeType, long contentLength,
                                           Duration validity) {
        try {
            Date expiration = Date.from(Instant.now().plus(validity));
            GeneratePresignedUrlRequest req = new GeneratePresignedUrlRequest(bucket, objectKey,
                    HttpMethodName.PUT);
            req.setExpiration(expiration);
            if (mimeType != null && !mimeType.isBlank()) {
                req.setContentType(mimeType);
            }
            String url = cosClient.generatePresignedUrl(req).toString();
            return new SignedUploadUrl(
                    extractFileIdFromKey(objectKey),
                    url,
                    "PUT",
                    mimeType == null ? Map.of() : Map.of("Content-Type", mimeType),
                    Instant.ofEpochMilli(expiration.getTime()));
        } catch (CosClientException ex) {
            throw new FileException(FileException.Code.BACKEND_IO_ERROR,
                    "COS 签名上传 URL 生成失败: " + objectKey, ex);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            cosClient.deleteObject(bucket, objectKey);
        } catch (CosClientException ex) {
            throw new FileException(FileException.Code.BACKEND_IO_ERROR,
                    "COS 删除失败: " + objectKey, ex);
        }
    }

    @Override
    public BackendObjectInfo metadata(String objectKey) {
        try {
            if (!cosClient.doesObjectExist(bucket, objectKey)) {
                return null;
            }
            ObjectMetadata m = cosClient.getObjectMetadata(bucket, objectKey);
            return new BackendObjectInfo(m.getContentLength(), m.getContentType());
        } catch (CosClientException ex) {
            throw new FileException(FileException.Code.BACKEND_IO_ERROR,
                    "COS 元数据读取失败: " + objectKey, ex);
        }
    }

    /** {@code FileStorageFactory} 在配置变更 / 关闭时调。 */
    public void shutdown() {
        try {
            cosClient.shutdown();
        } catch (Exception ex) {
            log.warn("[FileStorage:TencentCos] shutdown 异常: {}", ex.toString());
        }
    }

    private static String extractFileIdFromKey(String objectKey) {
        int idx = objectKey.lastIndexOf('/');
        return idx < 0 ? objectKey : objectKey.substring(idx + 1);
    }
}
