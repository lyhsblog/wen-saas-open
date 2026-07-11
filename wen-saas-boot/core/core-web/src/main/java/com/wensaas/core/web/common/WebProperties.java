package com.wensaas.core.web.common;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * {@code wen-saas-boot.web.*} 配置。
 */
@ConfigurationProperties(prefix = "wen-saas-boot.web")
@Getter
@Setter
public class WebProperties {

    private final Cors cors = new Cors();

    @Getter
    @Setter
    public static class Cors {
        /** 允许来源；空列表 = 关 CORS。 */
        private List<String> allowedOrigins = List.of();
        private List<String> allowedMethods = List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
        private List<String> allowedHeaders = List.of("*");
        private List<String> exposedHeaders = List.of("X-Request-Id");
        private boolean allowCredentials = true;
        private Duration maxAge = Duration.ofMinutes(30);

    }
}
