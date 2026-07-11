package com.wensaas.core.api.file;

/**
 * 文件存储业务异常。HTTP 适配（400/404/409 等）由 web 层 ExceptionAdvice 完成。
 */
public class FileException extends RuntimeException {

    public enum Code {
        /** 文件元数据不存在（按 fileId 找不到，或已物理清理）。 */
        FILE_NOT_FOUND,
        /** size_bytes 超过 {@code wen-saas-boot.file.max-size}。 */
        SIZE_EXCEEDED,
        /** 当前租户既无 sys_storage_config active 配置、平台默认后端也未启用。 */
        NO_ACTIVE_BACKEND,
        /** 上传确认时文件状态不是 uploading，或后端找不到该对象。 */
        UPLOAD_NOT_CONFIRMABLE,
        /** 后端配置不合法（缺失字段 / endpoint 解析失败 / 凭证解密失败等）。 */
        INVALID_BACKEND_CONFIG,
        /** 测试连接失败（凭证无效 / Bucket 不存在 / 权限不足 / 网络不通）。 */
        TEST_CONNECTION_FAILED,
        /** 业务模块的 FilePermissionChecker 拒绝下载（business-only 场景）。 */
        DOWNLOAD_FORBIDDEN,
        /** 后端 IO 异常（put / get / delete 等）。 */
        BACKEND_IO_ERROR,
        /** 凭证加解密异常（密钥变更 / 密文被篡改 / Base64 损坏等）。 */
        CREDENTIAL_DECRYPT_ERROR,
        /** 切换存储后端时新配置已存在（同 tenant_id 多个 active 冲突）。 */
        DUPLICATE_ACTIVE_CONFIG,

        // ===== v2 链接分享 =====
        /** 分享 token 不存在 / 已删除 / 跨租户访问（不暴露存在性，统一映射到此 Code）。 */
        SHARE_NOT_FOUND,
        /** 分享已过期（expires_at < now）。 */
        SHARE_EXPIRED,
        /** 分享已撤销（status='revoked'）。 */
        SHARE_REVOKED,
        /** 分享需要密码但调用方未提供。 */
        SHARE_PASSWORD_REQUIRED,
        /** 分享密码错误。 */
        SHARE_PASSWORD_WRONG,
        /** 分享下载次数已达上限。 */
        SHARE_DOWNLOAD_LIMIT_EXCEEDED
    }

    private final Code code;

    public FileException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public FileException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
