package com.wensaas.core.impl.dict;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * sys_dict_item Mapper。简单 CRUD 走 BaseMapper；复杂查询走
 * {@code mapper/dict/SysDictItemMapper.xml}。
 */
@Mapper
public interface SysDictItemMapper extends BaseMapper<SysDictItem> {

    /**
     * 取某分组的"租户视角"启用项：合并 tenant_id=0 + {@code tenantId}，按 sort_order/code 排序。
     * <p>本查询不依赖 MP TenantLine 注入；调用方需在外层 {@code InterceptorIgnoreHelper.handle(tenantLine=true)}。
     */
    List<SysDictItem> selectEnabledForTenant(@Param("groupCode") String groupCode,
                                              @Param("tenantId") long tenantId);

    /** 取某分组的"租户视角"全部项（含已停用）。调用方需先 {@code InterceptorIgnoreHelper.handle(tenantLine=true)}。 */
    List<SysDictItem> selectAllForTenant(@Param("groupCode") String groupCode,
                                          @Param("tenantId") long tenantId);

    /** 取某分组下租户自身的项数（用于"分组有项不可删"校验）。 */
    int countByTenantAndGroup(@Param("tenantId") long tenantId,
                                @Param("groupCode") String groupCode);

    /** 取某项的直接子项数。 */
    int countChildren(@Param("tenantId") long tenantId,
                       @Param("parentId") long parentId);

    /** 按 (tenant_id, group_code, code) 命中一条（未软删）。CUSTOM 写入唯一性校验用。 */
    SysDictItem selectByCode(@Param("tenantId") long tenantId,
                              @Param("groupCode") String groupCode,
                              @Param("code") String code);
}
