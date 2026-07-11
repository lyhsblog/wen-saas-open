package com.wensaas.core.impl.ops.health;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * {@code sys_tenant_health} Mapper。纯平台表，查询无需 tenant_id 注入。
 */
@Mapper
public interface SysTenantHealthMapper extends BaseMapper<SysTenantHealth> {
}
