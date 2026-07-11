package com.wensaas.core.web.common;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * CORS 装配。配置项见 {@link WebProperties.Cors}。
 *
 * <p>{@code allowed-origins} 为空时 CorsFilter 仍装配但不允许任何源（生产同源部署默认行为）。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(WebProperties.class)
public class CorsConfig {

    @Bean
    public CorsFilter corsFilter(WebProperties props) {
        CorsConfiguration cfg = new CorsConfiguration();
        WebProperties.Cors c = props.getCors();
        cfg.setAllowedOriginPatterns(c.getAllowedOrigins());
        cfg.setAllowedMethods(c.getAllowedMethods());
        cfg.setAllowedHeaders(c.getAllowedHeaders());
        cfg.setExposedHeaders(c.getExposedHeaders());
        cfg.setAllowCredentials(c.isAllowCredentials());
        cfg.setMaxAge(c.getMaxAge().getSeconds());

        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", cfg);
        return new CorsFilter(src);
    }
}
