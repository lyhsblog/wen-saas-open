package com.wensaas.core.impl.viewpref;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 列偏好 Mapper。TenantLine 自动按 tenant_id 隔离；user_id + view_key 在业务层过滤。
 */
@Mapper
public interface SysUserViewPrefMapper extends BaseMapper<SysUserViewPref> {
}
