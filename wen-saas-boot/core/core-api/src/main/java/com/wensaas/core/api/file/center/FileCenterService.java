package com.wensaas.core.api.file.center;

import com.wensaas.core.api.file.FileUploadResult;

import java.io.InputStream;
import java.util.List;

/**
 * 文件中心高层业务 API。
 *
 * <p>建立在 {@code FileService}（存储 / 签名 URL）之上，补「文件中心」维度：
 * 三空间（我的 / 共享 / 回收站）、文件夹树、回收站还原 / 彻底删、容量配额。
 * 下载签名 URL 仍走 {@code FileService.signedDownloadUrl}（前端复用 /api/v1/files/{id}/url），
 * 链接分享走 {@code FileShareService}。
 *
 * <p>空间语义：{@code space=mine} 按 {@code uploaded_by=当前用户} 隔离；{@code space=shared} 全租户可见。
 */
public interface FileCenterService {

    /** 空间常量。 */
    String SPACE_MINE = "mine";
    String SPACE_SHARED = "shared";

    // ---------- 文件夹 ----------

    /** 新建文件夹。parentId 为 null = 该 space 根目录。深度超 5 / 同级重名 → 抛异常。 */
    FolderView createFolder(String space, Long parentId, String name);

    /** 重命名文件夹。 */
    FolderView renameFolder(Long folderId, String newName);

    /** 移动文件夹到新父目录（newParentId 为 null = 移到根）。校验不成环 + 子树移动后深度 ≤ 5。 */
    void moveFolder(Long folderId, Long newParentId);

    /** 删除文件夹（级联软删整棵子树 + 其下文件，进回收站）。 */
    void deleteFolder(Long folderId);

    // ---------- 列表 / 上传 ----------

    /** 列出某目录下的子文件夹 + 文件（分页）+ 面包屑。 */
    FileCenterListView list(String space, Long folderId, String keyword, int page, int size);

    /** 上传到指定空间 / 目录（≤ maxSize）。重名自动加 (2)(3) 后缀；超配额抛异常。 */
    FileUploadResult upload(String space, Long folderId, String fileName, String mimeType,
                            long sizeBytes, InputStream content);

    /** 重命名文件。 */
    void renameFile(String fileId, String newName);

    /** 移动文件到目标空间 / 目录（重名自动加后缀）。 */
    void moveFile(String fileId, String targetSpace, Long targetFolderId);

    /** 软删文件（进回收站）。 */
    void deleteFile(String fileId);

    // ---------- 回收站 ----------

    /** 回收站列表（文件 + 文件夹「删除根」合并，按删除时间倒序，内存分页）。 */
    RecycleListView listRecycle(String space, int page, int size);

    /** 还原（文件 / 文件夹）。父目录已不存在则改挂 space 根。 */
    void restore(List<String> fileIds, List<Long> folderIds);

    /** 彻底删除（文件删后端对象 + 元数据；文件夹递归物理删元数据）。 */
    void purge(List<String> fileIds, List<Long> folderIds);

    // ---------- 配额 ----------

    /** 当前租户容量配额（已用 / 总量）。 */
    QuotaView quota();
}
