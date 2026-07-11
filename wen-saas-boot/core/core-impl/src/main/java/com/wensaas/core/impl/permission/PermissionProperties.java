package com.wensaas.core.impl.permission;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;

/**
 * 权限引擎配置。前缀 {@code wen-saas-boot.permission}。
 */
@ConfigurationProperties(prefix = "wen-saas-boot.permission")
@Getter
@Setter
public class PermissionProperties {

    /** 是否启用权限引擎（包括 {@code @RequirePermission} AOP 与数据权限拦截器）。 */
    private boolean enabled = true;

    /** Redis 缓存 TTL。默认 1 小时。 */
    private Duration cacheTtl = Duration.ofHours(1);

}
