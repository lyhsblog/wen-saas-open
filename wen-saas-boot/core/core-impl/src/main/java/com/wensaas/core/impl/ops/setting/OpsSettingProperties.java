package com.wensaas.core.impl.ops.setting;

import org.springframework.boot.context.properties.ConfigurationProperties;
import lombok.Getter;
import lombok.Setter;

/**
 * 运营系统参数（{@code sys_setting}）缓存配置；走统一 CacheRegion，本地条目上限见
 * {@code wen-saas-boot.cache.region-local-cache-size}。
 */
@ConfigurationProperties(prefix = "wen-saas-boot.ops.setting")
@Getter
@Setter
public class OpsSettingProperties {

    /** 缓存 TTL（秒），默认 60 秒（本地兜底；精确失效靠写后 evict 广播）。 */
    private int cacheTtlSeconds = 60;

}
