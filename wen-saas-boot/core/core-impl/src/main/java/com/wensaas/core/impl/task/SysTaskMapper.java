package com.wensaas.core.impl.task;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SysTaskMapper extends BaseMapper<SysTask> {

    /** 同租户同 handler 仍在运行（RUNNING / CANCELLING）的任务计数；用于并发控制。 */
    int countRunningByHandler(@Param("tenantId") Long tenantId, @Param("handlerCode") String handlerCode);

    /** 按提交人列出（按 created_at DESC）。 */
    List<SysTask> selectByCreator(@Param("tenantId") Long tenantId,
                                  @Param("creatorId") Long creatorId,
                                  @Param("offset") int offset,
                                  @Param("limit") int limit);

    /** 按提交人分页查询，status / type 为 null 时不过滤（任务中心列表）。 */
    List<SysTask> selectMine(@Param("tenantId") Long tenantId,
                             @Param("creatorId") Long creatorId,
                             @Param("status") String status,
                             @Param("type") String type,
                             @Param("offset") int offset,
                             @Param("limit") int limit);

    /** 与 {@link #selectMine} 同条件的总数。 */
    long countMine(@Param("tenantId") Long tenantId,
                   @Param("creatorId") Long creatorId,
                   @Param("status") String status,
                   @Param("type") String type);

    /**
     * 过期清理 —— 按批捞出已过期任务（用于 ExpiredTaskCleanerJob）。
     *
     * <p>只取 {@code id / tenant_id / result_data}（清理任务行前先软删 result_data.fileId
     * 指向的结果文件）。<b>跨全部租户</b>，调用方须旁路 TenantLine；行删除由调用方按 id 批删。
     */
    List<SysTask> selectExpired(@Param("limit") int limit);

    /**
     * 补偿巡检 —— 跨租户扫描超时仍 PENDING 的任务（用于 PendingTaskReaperJob，M8）。
     *
     * <p>仅取 {@code status='PENDING'} 且 {@code created_at < before}。<b>跨全部租户</b>，
     * 由调用方挂 {@code @CrossTenantQuery} 绕过 TenantLine。
     *
     * <p>按 {@code id} 升序的 keyset 游标分页：重投不改任务状态（消费异步），故须用 {@code id > afterId}
     * 推进游标，否则同批 PENDING 行会被反复扫到、重复重投。首页传 {@code afterId=null}。
     */
    List<SysTask> selectPendingForRepublish(@Param("before") LocalDateTime before,
                                            @Param("afterId") Long afterId,
                                            @Param("limit") int limit);
}
