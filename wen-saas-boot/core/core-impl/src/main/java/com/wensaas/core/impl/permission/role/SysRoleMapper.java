package com.wensaas.core.impl.permission.role;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {

    /** 列租户所有角色（未软删，含停用）。 */
    List<SysRole> listForTenant(@Param("tenantId") long tenantId);

    /** code 是否冲突（排除自身）。 */
    int countByCode(@Param("tenantId") long tenantId,
                     @Param("code") String code,
                     @Param("selfId") Long selfId);

    /** name 是否冲突（排除自身）。 */
    int countByName(@Param("tenantId") long tenantId,
                     @Param("name") String name,
                     @Param("selfId") Long selfId);
}
