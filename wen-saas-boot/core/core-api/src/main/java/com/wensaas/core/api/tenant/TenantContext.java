package com.wensaas.core.api.tenant;

import java.util.function.Supplier;

/**
 * 租户上下文：ThreadLocal 持有当前请求的 {@code tenant_id}。
 *
 * <p>来源约定：
 * <ul>
 *   <li>Web 请求：由 Web 层过滤器从认证态（登录会话）解析写入；仅当 dev 开关
 *       {@code wen-saas-boot.auth.allow-tenant-id-header=true} 时才降级解析 {@code X-Tenant-Id} header。请求结束 clear</li>
 *   <li>异步任务 / MQ 消费 / 定时任务：调用前 {@link #runAs} 显式注入</li>
 * </ul>
 *
 * <p>运营场景使用 {@link #PLATFORM_TENANT_ID}（即 0）作为"特殊租户"。
 */
public final class TenantContext {

    /** 运营 / 平台数据的 tenant_id。混合表中 tenant_id = 0 表示该行属于平台。 */
    public static final long PLATFORM_TENANT_ID = 0L;

    private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(Long tenantId) {
        CURRENT.set(tenantId);
    }

    public static Long get() {
        return CURRENT.get();
    }

    /**
     * 获取当前 tenant_id；未设置抛出。底座代码在需要 tenant_id 必须存在的场景使用。
     */
    public static long require() {
        Long t = CURRENT.get();
        if (t == null) {
            throw new IllegalStateException(
                    "TenantContext 未设置。请确保请求经过 TenantContextWebFilter，"
                            + "或异步场景下显式调用 TenantContext.runAs(...)");
        }
        return t;
    }

    public static void clear() {
        CURRENT.remove();
    }

    /**
     * 在指定租户上下文内执行 {@code action}，结束后恢复原上下文（嵌套友好）。
     */
    public static void runAs(Long tenantId, Runnable action) {
        Long previous = CURRENT.get();
        CURRENT.set(tenantId);
        try {
            action.run();
        } finally {
            restore(previous);
        }
    }

    public static <T> T callAs(Long tenantId, Supplier<T> action) {
        Long previous = CURRENT.get();
        CURRENT.set(tenantId);
        try {
            return action.get();
        } finally {
            restore(previous);
        }
    }

    private static void restore(Long previous) {
        if (previous == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(previous);
        }
    }
}
