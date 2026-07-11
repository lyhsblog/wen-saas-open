package com.wensaas.core.impl.customfield;

import com.wensaas.core.api.customfield.CustomFieldDisplayResolver;
import com.wensaas.core.api.customfield.CustomFieldService;
import com.wensaas.core.api.dict.DictService;
import com.wensaas.core.impl.namesource.EntityNameResolver;
import com.wensaas.core.impl.namesource.EntitySearchService;
import com.wensaas.core.impl.spi.registry.CustomFieldObjectRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import com.wensaas.core.impl.customfield.def.CustomFieldDefManager;
import com.wensaas.core.impl.customfield.def.CustomFieldIndexExecutor;
import com.wensaas.core.impl.customfield.def.CustomFieldValueValidator;
import com.wensaas.core.impl.customfield.def.SysCustomFieldDefMapper;
import com.wensaas.core.impl.customfield.interceptor.CustomFieldDataLoader;
import com.wensaas.core.impl.customfield.interceptor.CustomFieldQueryRewriter;
import com.wensaas.core.impl.customfield.interceptor.CustomFieldWriteInterceptor;
import com.wensaas.core.impl.customfield.quota.CustomFieldQuotaManager;
import com.wensaas.core.impl.customfield.quota.SysCustomFieldQuotaMapper;
import com.wensaas.core.impl.customfield.resolver.CustomFieldDisplayResolverImpl;
import com.wensaas.core.impl.customfield.resolver.CustomFieldResolver;
import com.wensaas.core.impl.customfield.resolver.RedisCustomFieldResolver;

/* v2: WriteInterceptor / QueryRewriter / DisplayResolver 在 enabled=true 同时 interceptor-enabled=true 时装配。 */

/**
 * 自定义字段框架装配。
 *
 * <p>{@code wen-saas-boot.customfield.enabled=false} 时整体不装配；默认开启。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CustomFieldProperties.class)
@ConditionalOnProperty(prefix = "wen-saas-boot.customfield", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class CustomFieldConfiguration {

    @Bean
    public CustomFieldDataLoader customFieldDataLoader(SysCustomFieldDefMapper defMapper,
                                                          SysCustomFieldQuotaMapper quotaMapper) {
        return new CustomFieldDataLoader(defMapper, quotaMapper);
    }

    @Bean
    public CustomFieldResolver customFieldResolver(StringRedisTemplate redis,
                                                      CustomFieldDataLoader loader,
                                                      CustomFieldProperties props) {
        return new RedisCustomFieldResolver(redis, loader, props);
    }

    @Bean
    public CustomFieldQuotaManager customFieldQuotaManager(SysCustomFieldQuotaMapper mapper,
                                                              CustomFieldProperties props) {
        return new CustomFieldQuotaManager(mapper, props);
    }

    @Bean
    public CustomFieldIndexExecutor customFieldIndexExecutor(JdbcTemplate jdbc) {
        return new CustomFieldIndexExecutor(jdbc);
    }

    @Bean
    public CustomFieldValueValidator customFieldValueValidator(ObjectProvider<DictService> dictServiceProvider) {
        return new CustomFieldValueValidator(dictServiceProvider);
    }

    @Bean
    public CustomFieldService customFieldService(CustomFieldObjectRegistry objectRegistry,
                                                    CustomFieldResolver resolver,
                                                    CustomFieldQuotaManager quotaManager,
                                                    CustomFieldValueValidator valueValidator,
                                                    CustomFieldProperties props,
                                                    EntityNameResolver entityNameResolver,
                                                    EntitySearchService entitySearchService) {
        return new CustomFieldServiceImpl(objectRegistry, resolver, quotaManager, valueValidator,
                props, entityNameResolver, entitySearchService);
    }

    @Bean
    public CustomFieldDefManager customFieldDefManager(SysCustomFieldDefMapper defMapper,
                                                          CustomFieldQuotaManager quotaManager,
                                                          CustomFieldIndexExecutor indexExecutor,
                                                          CustomFieldObjectRegistry objectRegistry,
                                                          CustomFieldResolver resolver,
                                                          CustomFieldValueValidator valueValidator,
                                                          EntitySearchService entitySearchService) {
        return new CustomFieldDefManager(defMapper, quotaManager, indexExecutor,
                objectRegistry, resolver, valueValidator, entitySearchService);
    }

    // ─── v2 业务端拦截器与展示填充 ──────────────────────────────────────────

    @Bean
    @ConditionalOnProperty(prefix = "wen-saas-boot.customfield", name = "interceptor-enabled",
            havingValue = "true", matchIfMissing = true)
    public CustomFieldWriteInterceptor customFieldWriteInterceptor(
            ObjectProvider<CustomFieldResolver> resolverProvider,
            ObjectProvider<CustomFieldValueValidator> validatorProvider) {
        return new CustomFieldWriteInterceptor(resolverProvider, validatorProvider);
    }

    @Bean
    @ConditionalOnProperty(prefix = "wen-saas-boot.customfield", name = "interceptor-enabled",
            havingValue = "true", matchIfMissing = true)
    public CustomFieldQueryRewriter customFieldQueryRewriter(CustomFieldObjectRegistry registry,
                                                                ObjectProvider<CustomFieldResolver> resolverProvider) {
        return new CustomFieldQueryRewriter(registry, resolverProvider);
    }

    @Bean
    public CustomFieldDisplayResolver customFieldDisplayResolver(CustomFieldResolver customFieldResolver,
                                                                    ObjectProvider<DictService> dictServiceProvider,
                                                                    EntityNameResolver entityNameResolver) {
        return new CustomFieldDisplayResolverImpl(customFieldResolver, dictServiceProvider, entityNameResolver);
    }
}
