package com.wensaas.core.impl.openapi;

import com.wensaas.core.impl.file.CredentialCipher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import jakarta.annotation.PostConstruct;
import com.wensaas.core.impl.openapi.calllog.ApiCallBatchBuffer;
import com.wensaas.core.impl.openapi.calllog.ApiCallEventConsumer;
import com.wensaas.core.impl.openapi.calllog.ApiCallEventPublisher;
import com.wensaas.core.impl.openapi.calllog.ApiCallLogFilter;
import com.wensaas.core.impl.openapi.calllog.RocketMqApiCallEventPublisher;
import com.wensaas.core.impl.openapi.calllog.SysApiCallLogMapper;
import com.wensaas.core.impl.openapi.gateway.AppKeyAuthenticationFilter;
import com.wensaas.core.impl.openapi.gateway.HmacSignatureVerifier;
import com.wensaas.core.impl.openapi.gateway.IpWhitelistMatcher;
import com.wensaas.core.impl.openapi.key.ApiKeyResolver;
import com.wensaas.core.impl.openapi.key.SysApiKeyMapper;
import com.wensaas.core.impl.openapi.ratelimit.RateLimiter;
import com.wensaas.core.impl.openapi.ratelimit.RedisSlidingWindowRateLimiter;

/**
 * 开放 API 框架装配。
 *
 * <p>{@code wen-saas-boot.openapi.enabled=true}（默认）时启用 AppKey 鉴权 / 限流 / 调用日志整套管道。
 * 关闭时 Filter / Consumer / BatchBuffer 全部不装配，业务路径不受影响。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OpenApiProperties.class)
@ConditionalOnProperty(prefix = "wen-saas-boot.openapi", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class OpenApiConfiguration {

    private static final Logger log = LoggerFactory.getLogger(OpenApiConfiguration.class);

    private final OpenApiProperties props;

    public OpenApiConfiguration(OpenApiProperties props) {
        this.props = props;
    }

    @PostConstruct
    public void logReady() {
        log.info("[OpenAPI] enabled, path-prefix={}, rate-limit={}/{}",
                props.getPathPrefix(),
                props.getRateLimit().getDefaultLimit(),
                props.getRateLimit().getWindow());
    }

    @Bean
    public ApiKeyResolver apiKeyResolver(SysApiKeyMapper mapper,
                                         CredentialCipher cipher,
                                         ObjectProvider<StringRedisTemplate> redisProvider,
                                         OpenApiProperties props) {
        return new ApiKeyResolver(mapper, cipher, redisProvider.getIfAvailable(), props);
    }

    @Bean
    public com.wensaas.core.impl.openapi.key.ApiKeyAdminService apiKeyAdminService(
            SysApiKeyMapper mapper, CredentialCipher cipher, ApiKeyResolver resolver) {
        return new com.wensaas.core.impl.openapi.key.ApiKeyAdminService(mapper, cipher, resolver);
    }

    @Bean
    public HmacSignatureVerifier hmacSignatureVerifier() {
        return new HmacSignatureVerifier();
    }

    @Bean
    public IpWhitelistMatcher ipWhitelistMatcher() {
        return new IpWhitelistMatcher();
    }

    @Bean
    public RateLimiter rateLimiter(StringRedisTemplate redisTemplate, OpenApiProperties props) {
        return new RedisSlidingWindowRateLimiter(redisTemplate, props);
    }

    @Bean
    public AppKeyAuthenticationFilter appKeyAuthenticationFilter(ApiKeyResolver resolver,
                                                                 HmacSignatureVerifier verifier,
                                                                 IpWhitelistMatcher ipMatcher,
                                                                 RateLimiter rateLimiter,
                                                                 com.wensaas.core.api.idempotent.IdempotencyGuard idempotencyGuard,
                                                                 OpenApiProperties props) {
        return new AppKeyAuthenticationFilter(resolver, verifier, ipMatcher, rateLimiter, idempotencyGuard, props);
    }

    @Bean
    @ConditionalOnProperty(prefix = "wen-saas-boot.openapi.call-log", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public ApiCallEventPublisher apiCallEventPublisher(
            ObjectProvider<org.apache.rocketmq.client.core.RocketMQClientTemplate> templateProvider,
            OpenApiProperties props) {
        return new RocketMqApiCallEventPublisher(templateProvider, props);
    }

    @Bean
    @ConditionalOnProperty(prefix = "wen-saas-boot.openapi.call-log", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public ApiCallLogFilter apiCallLogFilter(ApiCallEventPublisher publisher, OpenApiProperties props,
                                             com.wensaas.core.impl.pkg.ModuleApiRouteRegistry routeRegistry) {
        return new ApiCallLogFilter(publisher, props, routeRegistry);
    }

    @Bean
    @ConditionalOnProperty(prefix = "wen-saas-boot.openapi.call-log", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public ApiCallBatchBuffer apiCallBatchBuffer(SysApiCallLogMapper mapper, OpenApiProperties props) {
        return new ApiCallBatchBuffer(mapper, props);
    }

    @Bean
    public com.wensaas.core.impl.openapi.calllog.ApiCallLogQueryService apiCallLogQueryService(
            SysApiCallLogMapper mapper) {
        return new com.wensaas.core.impl.openapi.calllog.ApiCallLogQueryService(mapper);
    }

    @Bean
    @ConditionalOnProperty(prefix = "wen-saas-boot.openapi.call-log", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    @ConditionalOnProperty(name = "wen-saas-boot.mq.consumers-enabled",
            havingValue = "true", matchIfMissing = true)
    public ApiCallEventConsumer apiCallEventConsumer(ApiCallBatchBuffer buffer,
                                                      com.wensaas.core.impl.tenant.web.TenantActivityGuard activityGuard,
                                                      OpenApiProperties props,
                                                      com.wensaas.core.api.mq.MqConsumeSupport mqSupport) {
        return new ApiCallEventConsumer(buffer, activityGuard, props, mqSupport);
    }
}
