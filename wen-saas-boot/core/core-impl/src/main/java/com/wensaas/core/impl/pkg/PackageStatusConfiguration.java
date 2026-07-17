package com.wensaas.core.impl.pkg;

import com.wensaas.core.api.cache.CacheRegionFactory;
import com.wensaas.core.impl.tenant.web.TenantActivityGuard;
import com.wensaas.core.impl.tenant.TenantProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 套餐状态校验组件装配。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TenantProperties.class)
public class PackageStatusConfiguration {

    @Bean
    public PackageStatusGuard packageStatusGuard(TenantPlansResolver resolver,
                                                   CacheRegionFactory cacheRegionFactory,
                                                   TenantProperties props) {
        return new PackageStatusGuard(resolver, cacheRegionFactory, props);
    }

    @Bean
    public PackageStatusWebFilter packageStatusWebFilter(PackageStatusGuard guard,
                                                          ModuleApiRouteRegistry routeRegistry) {
        return new PackageStatusWebFilter(guard, routeRegistry);
    }

    @Bean
    public TenantCacheEvictor tenantCacheEvictor(TenantActivityGuard activityGuard,
                                                  ObjectProvider<PackageStatusGuard> packageStatusGuard) {
        return new TenantCacheEvictor(activityGuard, packageStatusGuard);
    }

    @Bean
    public PackageSpiSynchronizer packageSpiSynchronizer(SysPackageMapper packageMapper,
                                                          SysPackageModuleMapper packageModuleMapper) {
        return new PackageSpiSynchronizer(packageMapper, packageModuleMapper);
    }
}
