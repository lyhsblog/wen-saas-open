package com.wensaas.core.impl.demoguard;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * 演示守卫装配。仅 {@code wen-saas-boot.demo-guard.enabled=true} 时生效，关闭时零 Bean。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DemoGuardProperties.class)
@ConditionalOnProperty(prefix = "wen-saas-boot.demo-guard", name = "enabled", havingValue = "true")
public class DemoGuardConfiguration {

    @Bean
    public DemoGuardFilter demoGuardFilter(DemoGuardProperties properties) {
        return new DemoGuardFilter(properties.getRules());
    }

    @Bean
    public FilterRegistrationBean<DemoGuardFilter> demoGuardFilterRegistration(DemoGuardFilter filter) {
        FilterRegistrationBean<DemoGuardFilter> reg = new FilterRegistrationBean<>(filter);
        reg.addUrlPatterns("/*");
        // AuthenticationFilter(+50) 之前：拦截不依赖认证身份，最早短路
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 45);
        return reg;
    }
}
