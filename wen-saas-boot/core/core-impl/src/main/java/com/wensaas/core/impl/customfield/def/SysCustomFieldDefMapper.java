package com.wensaas.core.impl.customfield.def;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * sys_custom_field_def Mapper。简单 CRUD 走 BaseMapper；复杂查询走
 * {@code mapper/customfield/SysCustomFieldDefMapper.xml}。
 *
 * <p>调用方走 MP TenantLine 自动注入 {@code tenant_id} 过滤（自定义字段是租户级数据）。
 */
@Mapper
public interface SysCustomFieldDefMapper extends BaseMapper<SysCustomFieldDef> {

    /** 按 (tenant_id, object_code) 列出未软删字段，按 display_order, id 升序。 */
    List<SysCustomFieldDef> listByTenantAndObject(@Param("tenantId") long tenantId,
                                                    @Param("objectCode") String objectCode);

    /** 按 (tenant_id, object_code, field_code) 精确取未软删字段。 */
    SysCustomFieldDef findByTenantObjectAndCode(@Param("tenantId") long tenantId,
                                                  @Param("objectCode") String objectCode,
                                                  @Param("fieldCode") String fieldCode);

    /**
     * 跨租户统计：除 {@code excludeFieldId} 外，还有多少未软删字段对同一 {@code (object_code, field_code)}
     * 仍索引化。表达式索引名 {@code idx_{table}_extra_{field}} 跨租户共享，物理 DROP 前用本计数判断
     * 是否仍有其他租户在用（架构审查 M13）。
     *
     * <p><b>本查询故意不带 {@code tenant_id} 条件</b>：调用方须用
     * {@link com.wensaas.core.impl.tenant.isolation.IgnoreStrategyScopes#enter} 旁路 MP TenantLine，否则会被
     * 注入当前租户过滤、统计不到其他租户。
     */
    long countOtherIndexedPeers(@Param("objectCode") String objectCode,
                                @Param("fieldCode") String fieldCode,
                                @Param("excludeFieldId") long excludeFieldId);
}
