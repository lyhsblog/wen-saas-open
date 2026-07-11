package com.wensaas.core.impl.async.integration;

import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.auth.user.UserPrincipal;
import com.wensaas.core.api.request.RequestContext;
import com.wensaas.core.api.tenant.TenantContext;
import com.wensaas.core.impl.tenant.integration.TestApp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @Async + ContextPropagatingTaskDecorator 端到端测试。
 *
 * <p>验证：
 * <ul>
 *   <li>主线程持有的 UserContext / TenantContext / RequestContext 在 @Async 方法执行线程内可见</li>
 *   <li>子线程结束后主线程上下文不被破坏</li>
 *   <li>主线程无上下文时 @Async 方法内三件套均为 null（不报错）</li>
 * </ul>
 */
@SpringBootTest(classes = {TestApp.class, AsyncContextPropagationIntegrationTest.AsyncBeanConfig.class},
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AsyncContextPropagationIntegrationTest {

    @Autowired AsyncTester asyncTester;

    @AfterEach
    void cleanup() {
        UserContext.clear();
        TenantContext.clear();
        RequestContext.clear();
    }

    @BeforeEach
    void clear() {
        UserContext.clear();
        TenantContext.clear();
        RequestContext.clear();
    }

    @Test
    void async_methodSeesMainThreadContext() throws Exception {
        UserPrincipal p = new UserPrincipal(7L, 9L, "alice", "s", false, 11L, false, null);
        UserContext.set(p);
        TenantContext.set(9L);
        RequestContext.set("req-A");

        CompletableFuture<AsyncTester.Snap> f = asyncTester.captureContextAsync();
        AsyncTester.Snap captured = f.get(3, TimeUnit.SECONDS);

        assertThat(captured.user).isEqualTo(p);
        assertThat(captured.tenant).isEqualTo(9L);
        assertThat(captured.request).isEqualTo("req-A");
    }

    @Test
    void async_mainThreadContextNotDisturbed() throws Exception {
        TenantContext.set(42L);
        asyncTester.captureContextAsync().get(3, TimeUnit.SECONDS);
        assertThat(TenantContext.get()).isEqualTo(42L); // 主线程仍是 42
    }

    @Test
    void async_emptyContext_runsWithoutError() throws Exception {
        // 主线程无上下文
        CompletableFuture<AsyncTester.Snap> f = asyncTester.captureContextAsync();
        AsyncTester.Snap captured = f.get(3, TimeUnit.SECONDS);
        assertThat(captured.user).isNull();
        assertThat(captured.tenant).isNull();
        assertThat(captured.request).isNull();
    }

    @Configuration
    static class AsyncBeanConfig {
        @Bean
        public AsyncTester asyncTester() {
            return new AsyncTester();
        }
    }

    @Component
    public static class AsyncTester {

        @Async
        public CompletableFuture<Snap> captureContextAsync() {
            Snap s = new Snap();
            s.user = UserContext.get();
            s.tenant = TenantContext.get();
            s.request = RequestContext.get();
            return CompletableFuture.completedFuture(s);
        }

        public static class Snap {
            public UserPrincipal user;
            public Long tenant;
            public String request;
        }
    }
}
