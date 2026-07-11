package com.wensaas.core.impl.ops.monitor;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * {@link SysModuleAccessLog} CRUD + 批量插入（mapper.xml 实现 insertBatch，显式带 tenant_id 列）。
 */
@Mapper
public interface SysModuleAccessLogMapper extends BaseMapper<SysModuleAccessLog> {

    /** 批量插入；调用方需保证 id / createdAt 已赋值。 */
    int insertBatch(@Param("list") List<SysModuleAccessLog> list);
}
