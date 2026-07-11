package com.wensaas.core.api.file;

import java.io.InputStream;
import java.time.Duration;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * 文件存储后端统一接口。
 *
 * <p>由 {@code FileStorageFactory} 按 {@code sys_storage_config} active 配置实例化对应实现
 * （{@code LocalFileStorageAdapter} / {@code AliyunOssFileStorageAdapter} / ...）。
 *
 * <p>业务模块 / 高层 {@link FileService} 调用此接口，不感知具体后端。
 *
 * <p>实现契约：
 * <ul>
 *   <li>所有方法的 {@code objectKey} 由底座按 {@code tenants/{tenant_id}/{yyyyMM}/{file_id}}
 *   规则生成，实现不应自行假定路径结构</li>
 *   <li>实现异常统一包装为 {@link FileException}，{@code code} 取 {@code BACKEND_IO_ERROR} 等</li>
 *   <li>实现应当是无状态的或线程安全的；底座可能在多线程并发调用同一实例</li>
 * </ul>
 */
public interface FileStorage {

    /** 后端类型。 */
    FileBackendType backendType();

    /** 上传字节流到指定 objectKey。{@code contentLength} 用于实现侧 chunked / 直传决策。 */
    void upload(String objectKey, InputStream content, long contentLength, String mimeType);

    /** 流式下载；调用方负责关闭。 */
    InputStream download(String objectKey);

    /**
     * 生成签名下载 URL。
     *
     * <p>LocalAdapter 返回带 HMAC token 的 {@code /api/v1/files/local-download} URL
     * （由 {@code LocalFileController} 校验签名后回流文件）；OSS 后端返回带签名的真实 URL。
     */
    String signedDownloadUrl(String objectKey, Duration validity);

    /**
     * 生成签名上传 URL（前端直传场景）。
     *
     * @param objectKey    上传目标 key
     * @param mimeType     客户端 PUT 时必须设置的 Content-Type
     * @param contentLength 字节数（部分后端用于校验）
     * @param validity     URL 有效期
     */
    SignedUploadUrl signedUploadUrl(String objectKey, String mimeType, long contentLength, Duration validity);

    /** 删除对象。对象不存在不抛错（幂等）。 */
    void delete(String objectKey);

    /**
     * 查询对象是否存在 + 真实大小 / mimeType。用于 confirmUpload 阶段验证。
     *
     * @return null 表示对象不存在
     */
    BackendObjectInfo metadata(String objectKey);

    /**
     * 后端真实对象基础信息（不同于 {@link FileMetadata}，这是从后端 HEAD 拿到的）。
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class BackendObjectInfo {
        private long sizeBytes;
        private String mimeType;

    }
}
