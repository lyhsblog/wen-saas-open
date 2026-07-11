package com.wensaas.core.api.request;

import java.util.function.Supplier;

/**
 * 请求上下文：ThreadLocal 持有当前请求的 {@code requestId}。
 *
 * <p>来源：
 * <ul>
 *   <li>Web 请求：由最外层 {@code RequestIdFilter} 在 {@code OncePerRequestFilter} 链头部写入；
 *       请求结束 clear。同时写入 MDC（key={@code requestId}），并回写 Response Header
 *       {@code X-Request-Id}。</li>
 *   <li>MQ 消费 / @Async：消费侧从消息体取出原始 {@code requestId} 调
 *       {@link #restoreFromMessage(String)} 还原。</li>
 * </ul>
 *
 * <p>与 {@link com.wensaas.core.api.tenant.TenantContext} / {@code UserContext} 对称。
 */
public final class RequestContext {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private RequestContext() {
    }

    public static void set(String requestId) {
        CURRENT.set(requestId);
    }

    public static String get() {
        return CURRENT.get();
    }

    /** 强制要求已设置；缺失抛 {@link IllegalStateException}。 */
    public static String require() {
        String id = CURRENT.get();
        if (id == null) {
            throw new IllegalStateException(
                    "RequestContext 未设置。请确保请求经过 RequestIdFilter，"
                            + "或异步场景下显式调用 RequestContext.runAs / restoreFromMessage");
        }
        return id;
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static void runAs(String requestId, Runnable action) {
        String previous = CURRENT.get();
        CURRENT.set(requestId);
        try {
            action.run();
        } finally {
            restore(previous);
        }
    }

    public static <T> T callAs(String requestId, Supplier<T> action) {
        String previous = CURRENT.get();
        CURRENT.set(requestId);
        try {
            return action.get();
        } finally {
            restore(previous);
        }
    }

    /** MQ 消费 / 异步任务入口处恢复消息体里的原始 requestId。 */
    public static void restoreFromMessage(String requestId) {
        if (requestId != null && !requestId.isBlank()) {
            CURRENT.set(requestId);
        }
    }

    private static void restore(String previous) {
        if (previous == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(previous);
        }
    }
}
