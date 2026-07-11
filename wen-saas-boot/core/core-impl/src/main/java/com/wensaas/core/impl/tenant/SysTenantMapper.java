package com.wensaas.core.impl.tenant;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

/**
 * sys_tenant Mapper。纯平台表，无 tenant_id；v1 仅用于登录时反查主管理员标识。
 */
@Mapper
public interface SysTenantMapper extends BaseMapper<SysTenant> {

    /**
     * 按 id 批量取名（含已软删租户）。原生 SQL 不受 {@code @TableLogic} 的 {@code deleted_at IS NULL}
     * 自动注入影响，专供需要展示"已删除租户真实名 + 标注"的场景（如平台监控资源占用 Top5）。
     * 仅回填 id / name / deleted_at 三列；调用方须保证 {@code ids} 非空。
     */
    @Select("""
            <script>
            SELECT id, name, deleted_at FROM sys_tenant WHERE id IN
            <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>
            </script>
            """)
    List<SysTenant> selectNamesIncludeDeleted(@Param("ids") Collection<Long> ids);
}
