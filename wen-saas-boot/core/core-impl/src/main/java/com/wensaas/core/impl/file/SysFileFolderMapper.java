package com.wensaas.core.impl.file;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * {@code sys_file_folder} Mapper。
 *
 * <p>活跃记录的简单 CRUD 走 {@link BaseMapper}（@TableLogic 自动跳过软删，TenantLine 自动隔离）；
 * 回收站相关（查 / 还原 / 物理删）走本接口手写 SQL，显式控制 {@code deleted_at} 以绕过 @TableLogic。
 */
@Mapper
public interface SysFileFolderMapper extends BaseMapper<SysFileFolder> {

    /** 列出某父目录下的活跃子文件夹（parentId 为 null 表示根目录；ownerId 为 null 表示 shared 空间不限 owner）。 */
    List<SysFileFolder> selectChildren(@Param("space") String space,
                                       @Param("ownerId") Long ownerId,
                                       @Param("parentId") Long parentId);

    /**
     * 活跃子树全部 id（含自身），递归 CTE。用于级联删 / 移动深度计算。
     *
     * <p>{@code @InterceptorIgnore(tenantLine)}：MP TenantLine 改写递归 CTE 时会把自引用别名
     * {@code sub} 误当真实表注入 {@code sub.tenant_id}，触发 PG「column sub.tenant_id does not exist」
     * → 删/还原文件夹 500（空文件夹尤为明显，曾误判为「删除无效」）。此处关闭自动改写，改在 SQL 内
     * 显式按 {@code tenant_id} 过滤，隔离不丢。
     */
    @InterceptorIgnore(tenantLine = "true")
    List<Long> selectSubtreeIds(@Param("rootId") Long rootId, @Param("tenantId") Long tenantId);

    /** 已软删子树全部 id（含自身），递归 CTE。用于整文件夹还原 / 彻底删。同 {@link #selectSubtreeIds} 关闭 TenantLine、SQL 内显式隔离。 */
    @InterceptorIgnore(tenantLine = "true")
    List<Long> selectDeletedSubtreeIds(@Param("rootId") Long rootId, @Param("tenantId") Long tenantId);

    /** 回收站「删除根」：已软删、且其父未被软删的文件夹（避免把级联子目录都铺到回收站列表）。 */
    List<SysFileFolder> selectRecycledRoots(@Param("space") String space,
                                            @Param("ownerId") Long ownerId);

    /** 按主键查（含已软删），用于还原 / 彻底删前校验。 */
    SysFileFolder selectByIdIncludeDeleted(@Param("id") Long id);

    /** 批量软删（显式 set deleted_at=now()）。 */
    int softDeleteByIds(@Param("ids") List<Long> ids);

    /** 批量还原（显式 set deleted_at=null，绕过 @TableLogic）。 */
    int restoreByIds(@Param("ids") List<Long> ids);

    /** 还原「删除根」并在父目录已不存在时改挂到 space 根（parent_id=null, depth=1）。 */
    int reattachToRoot(@Param("id") Long id);

    /** 子树批量平移层级（移动文件夹后维护 depth）。 */
    int shiftDepthByIds(@Param("ids") List<Long> ids, @Param("delta") int delta);

    /** 批量物理删除（彻底删除 / 清理 Job）。 */
    int physicalDeleteByIds(@Param("ids") List<Long> ids);

    /** 软删后超过 threshold 的文件夹（清理 Job 用，分页）。 */
    List<SysFileFolder> selectSoftDeletedBefore(@Param("threshold") LocalDateTime threshold,
                                                @Param("limit") int limit);
}
