package com.wensaas.core.impl.request;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Request ID 基础设施装配。无条件启用 —— 即使 {@code wen-saas-boot.audit.enabled=false}，
 * 仍要保证所有日志带上 {@code requestId}。
 */
@Configuration(proxyBeanMethods = false)
public class RequestConfiguration {

    @Bean
    public RequestIdFilter requestIdFilter() {
        return new RequestIdFilter();
    }

    /**
     * ① 内部 access log 过滤器。默认开启，
     * {@code wen-saas-boot.access-log.enabled=false} 可关。
     */
    @Bean
    @ConditionalOnProperty(name = "wen-saas-boot.access-log.enabled", havingValue = "true", matchIfMissing = true)
    public InternalAccessLogFilter internalAccessLogFilter(
            @Value("${wen-saas-boot.openapi.path-prefix:/api/v1/openapi}") String openApiPrefix) {
        return new InternalAccessLogFilter(openApiPrefix);
    }
}
