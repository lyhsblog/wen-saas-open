package com.wensaas.core.impl.async;

import org.springframework.core.task.TaskDecorator;

/**
 * Spring {@link TaskDecorator}：每次提交的 Runnable 自动捕获提交线程上下文，在执行线程内恢复 + finally close。
 *
 * <p>与 {@code AsyncContextConfiguration} 装配的 {@code taskExecutor} 协同；业务方写 {@code @Async}
 * 时无感拿到 UserContext + TenantContext + RequestContext + MDC。
 */
public class ContextPropagatingTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        ContextSnapshot snap = ContextSnapshot.capture();
        return () -> {
            try (ContextSnapshot.Restoration ignored = snap.restore()) {
                runnable.run();
            }
        };
    }
}
