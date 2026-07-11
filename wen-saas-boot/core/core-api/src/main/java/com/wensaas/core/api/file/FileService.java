package com.wensaas.core.api.file;

import java.time.Duration;
import java.util.List;

/**
 * 文件存储高层业务 API。业务模块的标准入口。
 *
 * <p>与 {@link FileStorage} 的关系：FileStorage 是后端适配器接口（local / oss / ...），
 * FileService 在它之上加：元数据持久化（sys_file）、租户隔离、上传状态机、权限校验
 * ({@link FilePermissionChecker})、Redis 缓存。
 */
public interface FileService {

    /**
     * 业务方直接上传（适合 ≤ 100MB 小文件）。底座写入后端 + 落 sys_file（state=active）。
     */
    FileUploadResult uploadDirect(FileUploadRequest request);

    /**
     * 前端直传申请签名 URL。底座只写 sys_file state=uploading，返回 PUT URL。
     */
    SignedUploadUrl requestSignedUpload(SignedUploadRequest request);

    /**
     * 客户端 PUT 完成后调此确认。底座校验后端真实存在，把 state 改为 active。
     */
    FileUploadResult confirmUpload(String fileId);

    /**
     * 生成下载签名 URL（默认 5 分钟）。Redis 缓存 4 分钟。
     *
     * <p>business-only 文件：调对应 {@link FilePermissionChecker}；未注册默认放行。
     * <p>file-center 文件：v1 默认放行（待 UI 会话补"上传人/共享权限"校验）。
     */
    String signedDownloadUrl(String fileId, Duration validity);

    /**
     * 服务端读取文件全部字节（受信内部调用：导入框架重读源文件、任务结果再处理等）。
     *
     * <p>仅按租户隔离（MP TenantLine，跨租户 → {@link FileException.Code#FILE_NOT_FOUND}），
     * 不做用户级 canDownload —— 面向后端自身流程而非终端用户下载。大文件慎用（一次性载入内存）。
     */
    byte[] readContent(String fileId);

    /** 软删（@TableLogic）；后端文件由定时任务在 30 天后物理清理。 */
    void delete(String fileId);

    /** 按 fileId 查元数据；不存在 / 已软删 → 抛 {@link FileException.Code#FILE_NOT_FOUND}。 */
    FileMetadata metadata(String fileId);

    /** 按业务对象列出文件（仅活跃记录，按 uploaded_at 倒序）。 */
    List<FileMetadata> listByOwner(String ownerModule, String ownerRefType, Long ownerRefId);
}
