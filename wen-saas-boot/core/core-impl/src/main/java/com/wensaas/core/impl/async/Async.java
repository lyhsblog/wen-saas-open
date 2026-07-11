package com.wensaas.core.impl.async;

import java.util.concurrent.Callable;

/**
 * 静态助手：手动用 Executor 提交 Runnable / Callable 时无感传递四件套。
 *
 * <p>使用场景：业务方持有自己的 Executor / CompletableFuture 链路，不走 Spring {@code @Async}
 * 时无法享受 {@link ContextPropagatingTaskDecorator}；此时显式调 {@code Async.runWithContext(...)}。
 *
 * <pre>
 *   executor.submit(Async.runWithContext(() -&gt; { ... }));
 *   CompletableFuture.supplyAsync(Async.callWithContext(() -&gt; ...), executor);
 * </pre>
 */
public final class Async {

    private Async() {
    }

    /** 包装 Runnable：捕获当前上下文，提交线程执行时恢复 + finally close。 */
    public static Runnable runWithContext(Runnable runnable) {
        ContextSnapshot snap = ContextSnapshot.capture();
        return () -> {
            try (ContextSnapshot.Restoration ignored = snap.restore()) {
                runnable.run();
            }
        };
    }

    /** 包装 Callable：同 {@link #runWithContext(Runnable)}。 */
    public static <T> Callable<T> callWithContext(Callable<T> callable) {
        ContextSnapshot snap = ContextSnapshot.capture();
        return () -> {
            try (ContextSnapshot.Restoration ignored = snap.restore()) {
                return callable.call();
            }
        };
    }
}
