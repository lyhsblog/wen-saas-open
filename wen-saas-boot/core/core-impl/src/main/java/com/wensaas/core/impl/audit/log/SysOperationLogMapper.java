package com.wensaas.core.impl.audit.log;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SysOperationLogMapper extends BaseMapper<SysOperationLog> {

    /** 批量 INSERT（用于 Consumer 端批量落库；JSONB 列走 JacksonTypeHandler）。 */
    int insertBatch(@Param("list") List<SysOperationLog> list);
}
