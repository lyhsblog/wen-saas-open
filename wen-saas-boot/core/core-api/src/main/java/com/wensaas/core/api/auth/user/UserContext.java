package com.wensaas.core.api.auth.user;

import java.util.function.Supplier;

/**
 * 当前请求的认证主体上下文（ThreadLocal）。
 *
 * <p>来源约定：
 * <ul>
 *   <li>Web 请求：由 AuthenticationFilter 解析 JWT 后写入；请求结束 clear</li>
 *   <li>异步任务 / MQ 消费 / 定时任务：调用前 {@link #runAs} 显式注入</li>
 * </ul>
 *
 * <p>与 {@link com.wensaas.core.api.tenant.TenantContext} 对称：UserContext 是认证主体的来源，
 * TenantContextWebFilter 从 UserContext 派生 TenantContext。
 */
public final class UserContext {

    private static final ThreadLocal<UserPrincipal> CURRENT = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(UserPrincipal principal) {
        CURRENT.set(principal);
    }

    public static UserPrincipal get() {
        return CURRENT.get();
    }

    /**
     * 获取当前认证主体；未认证抛出。
     */
    public static UserPrincipal require() {
        UserPrincipal p = CURRENT.get();
        if (p == null) {
            throw new IllegalStateException(
                    "UserContext 未设置。请确保请求经过 AuthenticationFilter，"
                            + "或异步场景下显式调用 UserContext.runAs(...)");
        }
        return p;
    }

    public static void clear() {
        CURRENT.remove();
    }

    /**
     * 在指定主体上下文内执行 {@code action}，结束后恢复原上下文（嵌套友好）。
     */
    public static void runAs(UserPrincipal principal, Runnable action) {
        UserPrincipal previous = CURRENT.get();
        CURRENT.set(principal);
        try {
            action.run();
        } finally {
            restore(previous);
        }
    }

    public static <T> T callAs(UserPrincipal principal, Supplier<T> action) {
        UserPrincipal previous = CURRENT.get();
        CURRENT.set(principal);
        try {
            return action.get();
        } finally {
            restore(previous);
        }
    }

    private static void restore(UserPrincipal previous) {
        if (previous == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(previous);
        }
    }
}
