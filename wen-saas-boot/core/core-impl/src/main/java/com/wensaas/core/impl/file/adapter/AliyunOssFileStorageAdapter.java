package com.wensaas.core.impl.file.adapter;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.aliyun.oss.model.ObjectMetadata;
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
import com.wensaas.core.impl.file.storage.FileStorageFactory;

/**
 * 阿里云 OSS 后端。
 *
 * <p>OssClient 是线程安全的重对象，{@code FileStorageFactory} 按 backendConfigId 缓存，
 * 切换 / 删除配置时通过 {@code shutdown()} 释放。
 */
public class AliyunOssFileStorageAdapter implements FileStorage {

    private static final Logger log = LoggerFactory.getLogger(AliyunOssFileStorageAdapter.class);

    private final OSS ossClient;
    private final String bucket;
    private final String endpoint;

    public AliyunOssFileStorageAdapter(OSS ossClient, String bucket, String endpoint) {
        this.ossClient = ossClient;
        this.bucket = bucket;
        this.endpoint = endpoint;
        log.info("[FileStorage:AliyunOss] ready, endpoint={}, bucket={}", endpoint, bucket);
    }

    @Override
    public FileBackendType backendType() {
        return FileBackendType.ALIYUN_OSS;
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
            ossClient.putObject(bucket, objectKey, content, meta);
        } catch (OSSException | com.aliyun.oss.ClientException ex) {
            throw new FileException(FileException.Code.BACKEND_IO_ERROR,
                    "OSS 写入失败: " + objectKey, ex);
        }
    }

    @Override
    public InputStream download(String objectKey) {
        try {
            return ossClient.getObject(bucket, objectKey).getObjectContent();
        } catch (OSSException | com.aliyun.oss.ClientException ex) {
            throw new FileException(FileException.Code.BACKEND_IO_ERROR,
                    "OSS 读取失败: " + objectKey, ex);
        }
    }

    @Override
    public String signedDownloadUrl(String objectKey, Duration validity) {
        try {
            Date expiration = Date.from(Instant.now().plus(validity));
            return ossClient.generatePresignedUrl(bucket, objectKey, expiration).toString();
        } catch (OSSException | com.aliyun.oss.ClientException ex) {
            throw new FileException(FileException.Code.BACKEND_IO_ERROR,
                    "OSS 签名 URL 生成失败: " + objectKey, ex);
        }
    }

    @Override
    public SignedUploadUrl signedUploadUrl(String objectKey, String mimeType, long contentLength,
                                           Duration validity) {
        try {
            Date expiration = Date.from(Instant.now().plus(validity));
            GeneratePresignedUrlRequest req = new GeneratePresignedUrlRequest(bucket, objectKey,
                    com.aliyun.oss.HttpMethod.PUT);
            req.setExpiration(expiration);
            if (mimeType != null && !mimeType.isBlank()) {
                req.setContentType(mimeType);
            }
            String url = ossClient.generatePresignedUrl(req).toString();
            return new SignedUploadUrl(
                    extractFileIdFromKey(objectKey),
                    url,
                    "PUT",
                    mimeType == null ? Map.of() : Map.of("Content-Type", mimeType),
                    Instant.ofEpochMilli(expiration.getTime()));
        } catch (OSSException | com.aliyun.oss.ClientException ex) {
            throw new FileException(FileException.Code.BACKEND_IO_ERROR,
                    "OSS 签名上传 URL 生成失败: " + objectKey, ex);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            ossClient.deleteObject(bucket, objectKey);
        } catch (OSSException | com.aliyun.oss.ClientException ex) {
            throw new FileException(FileException.Code.BACKEND_IO_ERROR,
                    "OSS 删除失败: " + objectKey, ex);
        }
    }

    @Override
    public BackendObjectInfo metadata(String objectKey) {
        try {
            if (!ossClient.doesObjectExist(bucket, objectKey)) {
                return null;
            }
            ObjectMetadata m = ossClient.getObjectMetadata(bucket, objectKey);
            return new BackendObjectInfo(m.getContentLength(), m.getContentType());
        } catch (OSSException | com.aliyun.oss.ClientException ex) {
            throw new FileException(FileException.Code.BACKEND_IO_ERROR,
                    "OSS 元数据读取失败: " + objectKey, ex);
        }
    }

    /** {@code FileStorageFactory} 在配置变更 / 关闭时调。 */
    public void shutdown() {
        try {
            ossClient.shutdown();
        } catch (Exception ex) {
            log.warn("[FileStorage:AliyunOss] shutdown 异常: {}", ex.toString());
        }
    }

    private static String extractFileIdFromKey(String objectKey) {
        int idx = objectKey.lastIndexOf('/');
        return idx < 0 ? objectKey : objectKey.substring(idx + 1);
    }
}
