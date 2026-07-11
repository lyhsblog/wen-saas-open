package com.wensaas.core.impl.audit.log;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ③ 数据变更日志读 mapper。仅查询（触发器写入），按 requestId 关联 ② 操作日志详情。
 */
@Mapper
public interface SysDataChangeLogMapper extends BaseMapper<SysDataChangeLog> {

    /**
     * 按 requestId 取同请求下的全部字段级变更，时间正序。
     * {@code changes} 以 {@code ::text} 取原始 jsonb 文本。
     * {@code from/to} 为分区裁剪窗口（可空；非空时收窄到 created_at 区间，命中少数分区）。
     */
    List<SysDataChangeLog> findByRequestId(@Param("requestId") String requestId,
                                            @Param("from") LocalDateTime from,
                                            @Param("to") LocalDateTime to);
}
