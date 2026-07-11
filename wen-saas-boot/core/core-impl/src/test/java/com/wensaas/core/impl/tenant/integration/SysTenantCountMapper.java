package com.wensaas.core.impl.tenant.integration;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import com.wensaas.core.impl.tenant.isolation.TenantLineHandlerImpl;

/**
 * 仅测试用：原始 SQL 查 sys_tenant 行数。
 *
 * <p>用于验证 TenantLineHandlerImpl 的 platform-only 白名单是否生效。若白名单未生效，
 * 拦截器会注入 {@code WHERE tenant_id = ?}，PostgreSQL 报 "column tenant_id does not exist"。
 *
 * <p>命名加 Count 后缀，避免与生产代码 {@code com.wensaas.core.impl.tenant.SysTenantMapper}
 * 同名（同名 @Mapper 会触发 Spring bean 冲突）。
 */
@Mapper
public interface SysTenantCountMapper {

    @Select("SELECT COUNT(*) FROM sys_tenant")
    int count();
}
