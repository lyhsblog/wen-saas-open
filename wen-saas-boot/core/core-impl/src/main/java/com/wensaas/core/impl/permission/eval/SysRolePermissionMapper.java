package com.wensaas.core.impl.permission.eval;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SysRolePermissionMapper extends BaseMapper<SysRolePermission> {

    /** 列角色的权限点 code 列表（升序）。 */
    List<String> listCodesByRole(@Param("tenantId") long tenantId,
                                   @Param("roleId") long roleId);

    /** 全部删除角色权限点（替换写入时先清后插）。 */
    int deleteByRole(@Param("tenantId") long tenantId, @Param("roleId") long roleId);
}
