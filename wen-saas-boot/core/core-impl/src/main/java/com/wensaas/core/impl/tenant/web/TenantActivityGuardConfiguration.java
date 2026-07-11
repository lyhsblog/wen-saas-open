package com.wensaas.core.impl.tenant.web;

import com.wensaas.core.api.cache.CacheRegionFactory;
import com.wensaas.core.impl.auth.filter.AuthPathMatcher;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.wensaas.core.impl.tenant.SysTenantMapper;
import com.wensaas.core.impl.tenant.TenantProperties;

/**
 * 租户活跃度校验组件装配。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TenantProperties.class)
public class TenantActivityGuardConfiguration {

    @Bean
    public TenantActivityGuard tenantActivityGuard(SysTenantMapper tenantMapper,
                                                    CacheRegionFactory cacheRegionFactory,
                                                    TenantProperties props) {
        return new TenantActivityGuard(tenantMapper, cacheRegionFactory, props);
    }

    @Bean
    public TenantActivityWebFilter tenantActivityWebFilter(TenantActivityGuard guard,
                                                           AuthPathMatcher authPathMatcher) {
        return new TenantActivityWebFilter(guard, authPathMatcher);
    }
}
