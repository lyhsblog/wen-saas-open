package com.wensaas.core.impl.permission.datascope;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * {@code sys_role_data_scope} Mapper。
 *
 * <p>角色管理写入 (角色, action) 数据范围行；解析读取走 {@code PermissionEvalMapper} 的定向连表查询。
 */
@Mapper
public interface SysRoleDataScopeMapper extends BaseMapper<SysRoleDataScope> {
}
