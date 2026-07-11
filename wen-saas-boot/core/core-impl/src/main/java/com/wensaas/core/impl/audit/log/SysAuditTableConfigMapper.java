package com.wensaas.core.impl.audit.log;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * ③ 列元数据读 mapper。表很小，渲染时一次性 selectList 全量、内存建索引。
 */
@Mapper
public interface SysAuditTableConfigMapper extends BaseMapper<SysAuditTableConfig> {
}
