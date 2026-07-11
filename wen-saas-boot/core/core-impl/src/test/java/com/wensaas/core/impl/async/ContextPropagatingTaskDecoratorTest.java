package com.wensaas.core.impl.async;

import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.auth.user.UserPrincipal;
import com.wensaas.core.api.request.RequestContext;
import com.wensaas.core.api.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ContextPropagatingTaskDecoratorTest {

    @AfterEach
    void cleanup() {
        UserContext.clear();
        TenantContext.clear();
        RequestContext.clear();
    }

    @Test
    void runnable_runsInSubThread_withCapturedContext() throws Exception {
        UserPrincipal p = new UserPrincipal(7L, 9L, "alice", "sess-1", false, 11L, false, null);
        UserContext.set(p);
        TenantContext.set(9L);
        RequestContext.set("req-A");

        ContextPropagatingTaskDecorator decorator = new ContextPropagatingTaskDecorator();
        AtomicReference<UserPrincipal> u = new AtomicReference<>();
        AtomicReference<Long> t = new AtomicReference<>();
        AtomicReference<String> r = new AtomicReference<>();
        Runnable decorated = decorator.decorate(() -> {
            u.set(UserContext.get());
            t.set(TenantContext.get());
            r.set(RequestContext.get());
        });

        ExecutorService exec = Executors.newSingleThreadExecutor();
        try {
            exec.submit(decorated).get(2, TimeUnit.SECONDS);
        } finally {
            exec.shutdownNow();
        }
        assertThat(u.get()).isEqualTo(p);
        assertThat(t.get()).isEqualTo(9L);
        assertThat(r.get()).isEqualTo("req-A");
    }

    @Test
    void subThread_contextClearedAfterRunnable() throws Exception {
        UserContext.set(new UserPrincipal(1L, 1L, "x", "s", false, null, false, null));
        TenantContext.set(1L);

        ContextPropagatingTaskDecorator decorator = new ContextPropagatingTaskDecorator();
        AtomicReference<UserPrincipal> afterInner = new AtomicReference<>();
        Runnable decorated = decorator.decorate(() -> { /* nothing */ });

        ExecutorService exec = Executors.newSingleThreadExecutor();
        try {
            exec.submit(() -> {
                // 子线程在 decorated 前 / 后断言
                UserContext.set(new UserPrincipal(99L, 99L, "pre", "s99", false, null, false, null));
                decorated.run();
                afterInner.set(UserContext.get());
            }).get(2, TimeUnit.SECONDS);
        } finally {
            exec.shutdownNow();
        }
        // close 应恢复到子线程 set 的 pre 状态，而非清空
        assertThat(afterInner.get().username()).isEqualTo("pre");
    }

    @Test
    void emptyContext_decorate_runsWithoutError() throws Exception {
        ContextPropagatingTaskDecorator decorator = new ContextPropagatingTaskDecorator();
        AtomicReference<UserPrincipal> u = new AtomicReference<>();
        Runnable decorated = decorator.decorate(() -> u.set(UserContext.get()));

        ExecutorService exec = Executors.newSingleThreadExecutor();
        try {
            exec.submit(decorated).get(2, TimeUnit.SECONDS);
        } finally {
            exec.shutdownNow();
        }
        assertThat(u.get()).isNull();
    }

    @Test
    void Async_runWithContext_propagates() throws Exception {
        TenantContext.set(42L);
        AtomicReference<Long> tenant = new AtomicReference<>();
        Runnable r = Async.runWithContext(() -> tenant.set(TenantContext.get()));
        TenantContext.set(100L); // 改主线程上下文不影响快照

        ExecutorService exec = Executors.newSingleThreadExecutor();
        try {
            exec.submit(r).get(2, TimeUnit.SECONDS);
        } finally {
            exec.shutdownNow();
        }
        assertThat(tenant.get()).isEqualTo(42L);
    }
}
