package com.wensaas.core.impl.tenant;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * sys_tenant_route_alias Mapper。纯平台表，无 tenant_id（路由位别名跨租户唯一）。
 */
@Mapper
public interface SysTenantRouteAliasMapper extends BaseMapper<SysTenantRouteAlias> {
}
