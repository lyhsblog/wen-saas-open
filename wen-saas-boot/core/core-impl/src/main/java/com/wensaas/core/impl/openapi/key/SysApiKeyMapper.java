package com.wensaas.core.impl.openapi.key;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wensaas.core.impl.tenant.PgJsonbTypeHandler;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import com.wensaas.core.impl.openapi.gateway.AppKeyAuthenticationFilter;

/**
 * {@link SysApiKey} CRUD。{@code selectActiveByApiKey} 走 {@code uk_apikey_lookup} partial unique 索引；
 * 该方法主动绕过 MP TenantLine（开放 API 请求进来时 TenantContext 尚未建立，
 * 由 {@code AppKeyAuthenticationFilter} 调 {@link com.baomidou.mybatisplus.core.toolkit.InterceptorIgnoreHelper} 处理)。
 *
 * <p>{@code @Results} 显式声明 {@code ip_whitelist} 走 {@link JacksonTypeHandler}：MP 的 autoResultMap
 * 只对 BaseMapper 内置 SQL 生效，自定义 {@code @Select} 必须自带 typeHandler 否则 JSONB 列被丢弃为 null。
 * 其余字段走 MyBatis PARTIAL 自动映射（下划线→驼峰）。
 */
@Mapper
public interface SysApiKeyMapper extends BaseMapper<SysApiKey> {

    @Results(value = {
            @Result(column = "ip_whitelist", property = "ipWhitelist", typeHandler = PgJsonbTypeHandler.class)
    })
    @Select("SELECT * FROM sys_api_key WHERE api_key = #{apiKey} AND status = 'active' LIMIT 1")
    SysApiKey selectActiveByApiKey(@Param("apiKey") String apiKey);

    /** last_used_at 节流回写（缓存 miss 时调用，频率 ≤ 每 authCacheTtl 一次）；同上绕过租户线场景。 */
    @org.apache.ibatis.annotations.Update(
            "UPDATE sys_api_key SET last_used_at = now() WHERE id = #{id}")
    int touchLastUsed(@Param("id") long id);
}
