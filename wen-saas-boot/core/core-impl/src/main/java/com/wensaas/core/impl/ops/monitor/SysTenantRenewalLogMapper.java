package com.wensaas.core.impl.ops.monitor;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * {@link SysTenantRenewalLog} CRUD（标准 BaseMapper，逐条 insert；写入量低）。
 */
@Mapper
public interface SysTenantRenewalLogMapper extends BaseMapper<SysTenantRenewalLog> {
}
