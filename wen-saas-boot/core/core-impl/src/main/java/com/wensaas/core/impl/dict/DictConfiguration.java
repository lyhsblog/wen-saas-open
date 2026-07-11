package com.wensaas.core.impl.dict;

import com.wensaas.core.api.cache.CacheRegionFactory;
import com.wensaas.core.api.dict.DictAdminService;
import com.wensaas.core.api.dict.DictService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.wensaas.core.impl.dict.resolver.DictDataLoader;
import com.wensaas.core.impl.dict.resolver.DictResolver;
import com.wensaas.core.impl.dict.resolver.RedisDictResolver;

/**
 * 字典框架装配。
 *
 * <p>{@code wen-saas-boot.dict.enabled=false} 时整体不装配；默认开启。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DictProperties.class)
@ConditionalOnProperty(prefix = "wen-saas-boot.dict", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class DictConfiguration {

    @Bean
    public DictExtraSchemaValidator dictExtraSchemaValidator() {
        return new DictExtraSchemaValidator();
    }

    @Bean
    public DictDataLoader dictDataLoader(SysDictGroupMapper groupMapper,
                                          SysDictItemMapper itemMapper) {
        return new DictDataLoader(groupMapper, itemMapper);
    }

    @Bean
    public DictResolver dictResolver(CacheRegionFactory cacheRegionFactory,
                                       DictDataLoader loader,
                                       DictProperties props) {
        return new RedisDictResolver(cacheRegionFactory, loader, props);
    }

    @Bean
    public DictService dictService(DictResolver resolver) {
        return new DictServiceImpl(resolver);
    }

    @Bean
    public DictAdminService dictAdminService(SysDictGroupMapper groupMapper,
                                              SysDictItemMapper itemMapper,
                                              DictExtraSchemaValidator validator,
                                              DictResolver resolver) {
        return new DictAdminServiceImpl(groupMapper, itemMapper, validator, resolver);
    }

    @Bean
    public DictSpiSynchronizer dictSpiSynchronizer(SysDictGroupMapper groupMapper,
                                                     SysDictItemMapper itemMapper,
                                                     DictExtraSchemaValidator validator) {
        return new DictSpiSynchronizer(groupMapper, itemMapper, validator);
    }
}
