package com.wensaas.core.impl.openapi;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/**
 * 开放 API 框架配置。
 *
 * <pre>
 * wen-saas-boot:
 *   openapi:
 *     enabled: true
 *     path-prefix: /api/v1/openapi
 *     permit-all: []                     # 子路径白名单（无 AppKey 即可访问）
 *     timestamp-skew: 5m
 *     auth-cache-ttl: 5m
 *     max-body-bytes: 1MB
 *     rate-limit:
 *       enabled: true
 *       window: 60s
 *       default-limit: 1000
 *       tenant-overrides: {}            # {tenantId: limit}
 *       key-prefix: wen-saas-boot:openapi:ratelimit
 *     call-log:
 *       enabled: true
 *       topic: wen_api_call_log
 *       consumer-group: wen-saas-boot-api-call-log
 *       batch-size: 100
 *       batch-flush-interval: 5s
 * </pre>
 */
@ConfigurationProperties(prefix = "wen-saas-boot.openapi")
@Getter
@Setter
public class OpenApiProperties {

    private boolean enabled = true;
    private String pathPrefix = "/api/v1/openapi";
    private List<String> permitAll = new ArrayList<>();
    private Duration timestampSkew = Duration.ofMinutes(5);
    private Duration authCacheTtl = Duration.ofMinutes(5);
    private DataSize maxBodyBytes = DataSize.ofMegabytes(1);

    private RateLimit rateLimit = new RateLimit();
    private CallLog callLog = new CallLog();

    @Getter
    @Setter
    public static class RateLimit {
        private boolean enabled = true;
        private Duration window = Duration.ofSeconds(60);
        private int defaultLimit = 1000;
        private Map<Long, Integer> tenantOverrides = new LinkedHashMap<>();
        private String keyPrefix = "wen-saas-boot:openapi:ratelimit";

        public int limitFor(Long tenantId) {
            Integer override = tenantOverrides.get(tenantId);
            return override == null ? defaultLimit : override;
        }
    }

    @Getter
    @Setter
    public static class CallLog {
        private boolean enabled = true;
        // RocketMQ topic 名约束 ^[%|a-zA-Z0-9_-]+$，禁用点号
        private String topic = "wen_api_call_log";
        private String consumerGroup = "wen-saas-boot-api-call-log";
        private int batchSize = 100;
        private Duration batchFlushInterval = Duration.ofSeconds(5);

    }
}
