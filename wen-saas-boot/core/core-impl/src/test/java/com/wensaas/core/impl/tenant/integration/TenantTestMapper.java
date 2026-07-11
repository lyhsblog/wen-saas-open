package com.wensaas.core.impl.tenant.integration;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TenantTestMapper {

    @Select("SELECT COUNT(*) FROM sys_tenant_test")
    int count();

    @Select("SELECT id FROM sys_tenant_test ORDER BY id")
    List<Long> findAllIds();
}
