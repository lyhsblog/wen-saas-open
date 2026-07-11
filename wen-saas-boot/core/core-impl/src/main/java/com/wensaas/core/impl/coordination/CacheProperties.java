package com.wensaas.core.impl.coordination;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;

/**
 * 缓存配置（{@link com.wensaas.core.api.cache.CacheRegion} 回源单飞与本地 L1 容量）。
 *
 * <pre>
 * wen-saas-boot:
 *   cache:
 *     single-flight-wait-time: 2s   # 单飞重建等待锁最长时长；超时降级直连加载
 *     region-local-cache-size: 10000 # 每个 CacheRegion 本地 L1 最大条目数（LRU 淘汰触发）
 * </pre>
 */
@ConfigurationProperties(prefix = "wen-saas-boot.cache")
@Getter
@Setter
public class CacheProperties {

    /** 单飞重建等待锁的最长时长；超时降级为直接加载（不写缓存），保证可用性不被锁阻塞。 */
    private Duration singleFlightWaitTime = Duration.ofSeconds(2);

    /** 每个 {@link com.wensaas.core.api.cache.CacheRegion} 本地 L1 最大条目数；超过按 LRU 淘汰。 */
    private int regionLocalCacheSize = 10_000;
}
