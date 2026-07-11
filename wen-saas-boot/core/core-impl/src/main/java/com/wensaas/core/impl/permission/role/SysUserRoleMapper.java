package com.wensaas.core.impl.permission.role;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {

    /** 列角色下成员 userId（去重 + 升序）。 */
    List<Long> listUserIdsByRole(@Param("tenantId") long tenantId,
                                  @Param("roleId") long roleId);

    /** 角色的成员数。 */
    int countByRole(@Param("tenantId") long tenantId, @Param("roleId") long roleId);

    /** 批量删除指定 (roleId, userIds)。 */
    int deleteByRoleAndUsers(@Param("tenantId") long tenantId,
                              @Param("roleId") long roleId,
                              @Param("userIds") List<Long> userIds);
}
