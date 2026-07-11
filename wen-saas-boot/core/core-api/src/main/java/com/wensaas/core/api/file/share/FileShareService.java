package com.wensaas.core.api.file.share;

import com.wensaas.core.api.file.FileException;
import com.wensaas.core.api.tenant.TenantContext;

import java.util.List;

/**
 * 文件链接分享 SPI。
 *
 * <p>P0 范围：分享仅限租户内成员访问（访问者需登录态，且租户 ID 与 share.tenantId 一致）。
 * 跨租户访问 / 不存在 / 已删除 统一抛 {@link FileException.Code#SHARE_NOT_FOUND}，不暴露存在性。
 *
 * <p>状态机：active → revoked（撤销同步软删）；过期由 expires_at 时间判定，不改 status。
 *
 * <p>密码：4-6 位明文 → BCrypt 哈希；空密码 = 无需校验。
 * 次数：原子 UPDATE WHERE download_count &lt; max_downloads（并发超限防御）。
 */
public interface FileShareService {

    /** 创建分享。fileId 必须存在且属于当前租户；返回快照供调用方展示。 */
    FileShareInfo createShare(FileShareCreateRequest request);

    /**
     * 查看分享快照（不消耗下载次数）。
     *
     * <p>抛错路径：
     * <ul>
     *   <li>{@link FileException.Code#SHARE_NOT_FOUND} 不存在 / 已删除 / 跨租户</li>
     *   <li>{@link FileException.Code#SHARE_EXPIRED} 过期</li>
     *   <li>{@link FileException.Code#SHARE_REVOKED} 已撤销</li>
     * </ul>
     */
    FileShareInfo getShare(String shareToken);

    /**
     * 消费分享（密码校验 + 计数自增 + 返回签名 URL）。原子 UPDATE 保证并发不超限。
     *
     * <p>抛错路径：{@link #getShare(String)} 全部抛错 +
     * {@link FileException.Code#SHARE_PASSWORD_REQUIRED} 需密码但未提供 +
     * {@link FileException.Code#SHARE_PASSWORD_WRONG} +
     * {@link FileException.Code#SHARE_DOWNLOAD_LIMIT_EXCEEDED}。
     *
     * @param passwordPlain 调用方传入的密码；分享无密码时此参数被忽略
     */
    FileShareConsumeResult consumeShare(String shareToken, String passwordPlain);

    /** 列出当前租户内某 fileId 的所有活跃（含已过期但未撤销）分享，按创建时间倒序。 */
    List<FileShareInfo> listShares(String fileId);

    /** 撤销分享（status=revoked + 软删）；跨租户视为 NOT_FOUND。 */
    void revokeShare(String shareToken);

    /**
     * 免登录查看分享快照（公开落地页 FC-901 用）。
     *
     * <p>与 {@link #getShare(String)} 的区别：调用方无登录态 / 无 {@link TenantContext}，
     * token 全局唯一、自携租户归属，因此<b>不做租户一致校验</b>（调用入口须以
     * {@code @CrossTenantQuery} 绕过 MP TenantLine）。抛错路径同 {@link #getShare(String)}
     * 去掉跨租户分支：NOT_FOUND / EXPIRED / REVOKED。
     */
    FileShareInfo getPublicShare(String shareToken);

    /**
     * 免登录消费分享并获取下载 URL（公开落地页 FC-901 用）。
     *
     * <p>语义同 {@link #consumeShare(String, String)}（密码校验 + 原子计数自增 + 签名 URL），
     * 但不做租户一致校验（同 {@link #getPublicShare(String)}）。调用入口须 {@code @CrossTenantQuery}。
     */
    FileShareConsumeResult consumePublicShare(String shareToken, String passwordPlain);
}
