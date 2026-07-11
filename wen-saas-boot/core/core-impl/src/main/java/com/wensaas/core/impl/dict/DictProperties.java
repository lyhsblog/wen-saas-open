package com.wensaas.core.impl.dict;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;

/**
 * 字典框架配置。前缀 {@code wen-saas-boot.dict}。
 */
@ConfigurationProperties(prefix = "wen-saas-boot.dict")
@Getter
@Setter
public class DictProperties {

    /** 是否启用字典框架（默认 true）。 */
    private boolean enabled = true;

    /** Redis 缓存 TTL。默认 1 小时。 */
    private Duration cacheTtl = Duration.ofHours(1);

}
