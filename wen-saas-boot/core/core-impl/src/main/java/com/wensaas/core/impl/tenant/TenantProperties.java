package com.wensaas.core.impl.tenant;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

/**
 * 多租户相关配置：当前仅维护「纯平台表白名单」——这些表不带 {@code tenant_id} 字段，
 * MyBatis 拦截器需跳过 {@code tenant_id} 注入。
 *
 * <pre>
 * wen-saas-boot:
 *   tenant:
 *     platform-only-tables:
 *       - sys_tenant
 *       - sys_business_module
 *       - ...
 * </pre>
 *
 * 默认包含底座已知的"纯平台表"（无 tenant_id 列、不参与租户线注入）+ {@code flyway_schema_history}。
 */
@ConfigurationProperties(prefix = "wen-saas-boot.tenant")
@Getter
@Setter
public class TenantProperties {

    private Set<String> platformOnlyTables = defaultPlatformOnly();
    private ActivityGuard activityGuard = new ActivityGuard();
    private PackageStatus packageStatus = new PackageStatus();

    /**
     * 租户前端（tenant-web）裸域 origin，用于后端拼装完整激活/设密码链接放进开通邮件：
     * 租户 code 作为子域前缀（{@code https://{code}.example.com/...}）。
     * 形如 {@code https://example.com}（不含子域、无尾部斜杠）；缺省 dev 本地端口。
     */
    private String webBaseUrl = "http://localhost:5174";

    /** 租户活跃度缓存配置（tenantId → status）；走统一 CacheRegion，本地条目上限见 wen-saas-boot.cache.region-local-cache-size。 */
    @Getter
    @Setter
    public static class ActivityGuard {
        /** 缓存 TTL（秒），默认 5 分钟（本地兜底；精确失效靠写后 evict 广播）。 */
        private int cacheTtlSeconds = 300;
    }

    public void setPlatformOnlyTables(Set<String> platformOnlyTables) {
        if (platformOnlyTables == null || platformOnlyTables.isEmpty()) {
            this.platformOnlyTables = defaultPlatformOnly();
        } else {
            Set<String> normalized = new LinkedHashSet<>();
            for (String t : platformOnlyTables) {
                if (t != null) normalized.add(t.toLowerCase());
            }
            this.platformOnlyTables = normalized;
        }
    }

    /** 套餐状态缓存配置（tenantId → ResolvedPlans）；走统一 CacheRegion，本地条目上限见 wen-saas-boot.cache.region-local-cache-size。 */
    @Getter
    @Setter
    public static class PackageStatus {
        /** 缓存 TTL（秒），默认 5 分钟（本地兜底；精确失效靠写后 evict 广播）。 */
        private int cacheTtlSeconds = 300;
    }

    private static Set<String> defaultPlatformOnly() {
        Set<String> s = new LinkedHashSet<>();
        s.add("sys_tenant");
        s.add("sys_business_module");
        s.add("sys_package");
        s.add("sys_package_module");
        s.add("sys_recommended_role_template");
        s.add("sys_recommended_role_template_package");
        s.add("sys_recommended_role_template_permission");
        s.add("sys_recommended_role_template_activation");
        s.add("sys_setting");
        s.add("sys_tenant_health");
        s.add("sys_tenant_renewal_log");
        // 路由位别名：old_code 全局唯一、reclaim 跨租户，不能按当前租户过滤（否则跨租户回收/解析失效）
        s.add("sys_tenant_route_alias");
        s.add("sys_module_access_log");
        // ③ 列元数据：全局配置表（无 tenant_id），渲染数据变更详情时查，必须跳过租户注入
        s.add("sys_audit_table_config");
        // MQ 死信兜底表（M7）：跨租户、监控线程无租户上下文写入，tenant_id 仅过滤列不注入
        s.add("sys_mq_dead_letter");
        s.add("flyway_schema_history");
        return s;
    }
}
