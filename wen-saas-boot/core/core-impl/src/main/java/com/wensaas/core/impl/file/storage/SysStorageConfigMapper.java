package com.wensaas.core.impl.file.storage;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SysStorageConfigMapper extends BaseMapper<SysStorageConfig> {

    /** 当前租户的 active 配置（最多一条；partial unique index 保障）。 */
    SysStorageConfig selectActive(@Param("tenantId") Long tenantId);

    /** 按 id 查（不限 status；可拿到 archived）。MP TenantLine 仍生效。 */
    SysStorageConfig selectByIdAnyStatus(@Param("id") Long id);

    /** 列出某租户所有配置（active + archived，按 created_at DESC）。 */
    List<SysStorageConfig> selectAllForTenant(@Param("tenantId") Long tenantId);

    /** 把当前租户所有 active 标 archived。返回更新行数。 */
    int archiveAllActive(@Param("tenantId") Long tenantId);
}
