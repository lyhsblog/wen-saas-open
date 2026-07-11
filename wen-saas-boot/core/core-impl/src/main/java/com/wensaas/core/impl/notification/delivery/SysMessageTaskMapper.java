package com.wensaas.core.impl.notification.delivery;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * {@code sys_message_task} Mapper。
 */
@Mapper
public interface SysMessageTaskMapper extends BaseMapper<SysMessageTask> {

    /** 按业务 taskId 查全部拆分行（含跨通道）。MP TenantLine 自动注入。 */
    List<SysMessageTask> selectByTaskId(@Param("taskId") String taskId);

    /** 批量插入 PENDING 任务行（广播按 收件人 × 通道 一次性落库，避免逐行 INSERT；mapper.xml 显式带 tenant_id 列）。 */
    int insertBatch(@Param("list") List<SysMessageTask> list);

    /** Consumer 成功路径：把指定行更新为 SENT。 */
    int markSent(@Param("id") long id,
                  @Param("retryCount") int retryCount,
                  @Param("sentAt") LocalDateTime sentAt);

    /** Consumer 失败路径：retry_count++ + error_reason。 */
    int markFailed(@Param("id") long id,
                    @Param("retryCount") int retryCount,
                    @Param("errorReason") String errorReason,
                    @Param("terminal") boolean terminal);

    /** Consumer 跳过路径（适配器声明不可触达）。 */
    int markSkipped(@Param("id") long id, @Param("errorReason") String errorReason);
}
