package com.wensaas.core.impl.dict;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * sys_dict_group Mapper。简单 CRUD 走 BaseMapper；复杂查询走
 * {@code mapper/dict/SysDictGroupMapper.xml}。
 */
@Mapper
public interface SysDictGroupMapper extends BaseMapper<SysDictGroup> {

    /**
     * 查询某分组的"租户视角"定义：合并 tenant_id=0（SYSTEM/MODULE）+ {@code tenantId}（CUSTOM）。
     * <p>当两者都有时按 tenant_id DESC 优先 CUSTOM。仅返回未软删的记录。
     * <p>本查询不依赖 MP TenantLine 注入；调用方需在外层 {@code InterceptorIgnoreHelper.handle(tenantLine=true)}。
     */
    SysDictGroup selectForTenant(@Param("groupCode") String groupCode,
                                  @Param("tenantId") long tenantId);

    /** 按 (tenant_id, code, source) 命中一条；MODULE 同步用。 */
    SysDictGroup selectByCodeAndSource(@Param("tenantId") long tenantId,
                                        @Param("groupCode") String groupCode,
                                        @Param("source") int source);

    /** 按 tenant_id=0 的 SYSTEM/MODULE 分组列表（启动期 schema 比对用）。 */
    List<SysDictGroup> selectAllPlatformLevel();

    /** 列租户视角下的全部分组（含已停用），合并 tenant_id IN (0, X)；按 source asc, code asc 排序。
     *  调用方需在外层 {@code InterceptorIgnoreHelper.handle(tenantLine=true)}。 */
    List<SysDictGroup> selectAllForTenant(@Param("tenantId") long tenantId);
}
