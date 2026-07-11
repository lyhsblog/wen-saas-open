package com.wensaas.crmdemo.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wensaas.crmdemo.entity.CrmDemoCustomer;
import org.apache.ibatis.annotations.Mapper;

/**
 * 客户 Mapper。活跃记录 CRUD 走 {@link BaseMapper}：
 * {@code @TableLogic} 自动跳过软删、TenantLine 自动隔离、DataPermission 自动按角色过滤、
 * CustomFieldQueryRewriter 自动翻译 {@code cf_*} 条件。
 */
@Mapper
public interface CrmDemoCustomerMapper extends BaseMapper<CrmDemoCustomer> {
}
