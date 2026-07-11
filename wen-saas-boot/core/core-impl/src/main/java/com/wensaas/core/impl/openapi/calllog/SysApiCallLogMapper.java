package com.wensaas.core.impl.openapi.calllog;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * {@link SysApiCallLog} CRUD + 批量插入（mapper.xml 实现 insertBatch）。
 */
@Mapper
public interface SysApiCallLogMapper extends BaseMapper<SysApiCallLog> {

    /** 批量插入；调用方需保证 id / createdAt 已赋值。 */
    int insertBatch(@Param("list") List<SysApiCallLog> list);
}
