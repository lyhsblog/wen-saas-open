package com.wensaas.core.impl.file;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SysFileMapper extends BaseMapper<SysFile> {

    /** 按 file_id 找活跃记录（自动跳过软删，MP @TableLogic 注入 deleted_at IS NULL）。 */
    SysFile selectByFileId(@Param("fileId") String fileId);

    /** 业务对象关联查询（按 uploaded_at DESC）。 */
    List<SysFile> selectByOwner(@Param("tenantId") Long tenantId,
                                @Param("ownerModule") String ownerModule,
                                @Param("ownerRefType") String ownerRefType,
                                @Param("ownerRefId") Long ownerRefId);

    /** 软删后超过 {@code threshold} 的元数据（用于 SoftDeletedFileCleanerJob，分页）。 */
    List<SysFile> selectSoftDeletedBefore(@Param("threshold") LocalDateTime threshold,
                                          @Param("limit") int limit);

    /** uploading 状态且 uploaded_at < threshold 的孤儿（用于 UploadingOrphanCleanerJob）。 */
    List<SysFile> selectUploadingOrphans(@Param("threshold") LocalDateTime threshold,
                                         @Param("limit") int limit);

    /** 物理删除（@TableLogic 之外的硬删除，仅由清理 Job 使用）。 */
    int physicalDelete(@Param("id") Long id);

    /**
     * 当前租户回收站（{@code deleted_at IS NOT NULL}）聚合统计（用于 RetentionService 留存总览）。
     * 租户线由 TenantLineInnerInterceptor 自动追加 {@code tenant_id} 过滤，故不在 SQL 内显式写。
     *
     * @param expiringThreshold 即将清理边界（{@code now - (retention - window)}）
     * @param overdueThreshold  已超期边界（{@code now - retention}）
     */
    RecycleBinAgg selectRecycleBinAgg(@Param("expiringThreshold") LocalDateTime expiringThreshold,
                                      @Param("overdueThreshold") LocalDateTime overdueThreshold);

    // ===================== 文件中心 =====================

    /**
     * 文件中心某目录下的活跃文件（分页，按 uploaded_at DESC）。
     *
     * @param uploadedBy 我的文件传当前用户；共享文件传 null（不限上传人）
     * @param folderId   目录 id；null=该 space 根目录
     * @param keyword    文件名模糊（null/空忽略）
     */
    IPage<SysFile> selectCenterPage(IPage<SysFile> page,
                                    @Param("space") String space,
                                    @Param("uploadedBy") Long uploadedBy,
                                    @Param("folderId") Long folderId,
                                    @Param("keyword") String keyword);

    /** 某目录下活跃文件名（用于上传重名后缀计算）。 */
    List<String> selectFileNamesInFolder(@Param("space") String space,
                                         @Param("uploadedBy") Long uploadedBy,
                                         @Param("folderId") Long folderId);

    /** 回收站直删文件（其所在文件夹未被软删的，即非文件夹级联带走的）。 */
    List<SysFile> selectRecycledFiles(@Param("space") String space,
                                      @Param("uploadedBy") Long uploadedBy);

    /** 按 file_id 查（含已软删），用于还原 / 彻底删前校验。 */
    SysFile selectByFileIdIncludeDeleted(@Param("fileId") String fileId);

    /** 取若干文件夹下的活跃文件 id（级联软删用）。 */
    List<Long> selectActiveFileIdsByFolderIds(@Param("folderIds") List<Long> folderIds);

    /** 取若干文件夹下的已软删文件（整文件夹还原 / 彻底删用）。 */
    List<SysFile> selectDeletedFilesByFolderIds(@Param("folderIds") List<Long> folderIds);

    /** 上传后回填文件中心维度（space / folder_id）。 */
    int bindCenterAttrs(@Param("fileId") String fileId,
                        @Param("space") String space,
                        @Param("folderId") Long folderId);

    /** 重命名 + 移动（space / folder_id / file_name），按 file_id 定位活跃文件。 */
    int updateCenterAttrs(@Param("fileId") String fileId,
                          @Param("space") String space,
                          @Param("folderId") Long folderId,
                          @Param("fileName") String fileName);

    /** 批量软删文件（显式 set deleted_at=now()）。 */
    int softDeleteByIds(@Param("ids") List<Long> ids);

    /** 还原单个文件（显式 set deleted_at=null + 改挂目标目录，绕过 @TableLogic）。 */
    int restoreFile(@Param("id") Long id, @Param("folderId") Long folderId);

    /** 还原若干文件夹下的已软删文件（整文件夹还原用）。 */
    int restoreFilesByFolderIds(@Param("folderIds") List<Long> folderIds);

    /** 当前租户活跃文件占用字节（配额已用量）。 */
    long sumActiveSize(@Param("tenantId") Long tenantId);
}
