package com.wensaas.core.impl.audit.aspect;

import com.wensaas.core.api.audit.AuditEvent;
import com.wensaas.core.api.audit.AuditLogRecorder;
import com.wensaas.core.api.audit.Auditable;
import com.wensaas.core.api.audit.LoginAuditEvent;
import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.auth.user.UserQueryService;
import com.wensaas.core.api.auth.user.UserPrincipal;
import com.wensaas.core.api.request.RequestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.wensaas.core.impl.audit.pending.PendingFailureAudits;

class AuditableAspectTest {

    private RecordingRecorder recorder;
    private DemoService proxy;

    @BeforeEach
    void setup() {
        recorder = new RecordingRecorder();
        DemoService target = new DemoService();
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.addAspect(new AuditableAspect(recorder, noopUserDirectory()));
        proxy = factory.getProxy();

        UserContext.set(new UserPrincipal(1001L, 99L, "tester", "sid-x", false, 5001L, false, null));
        RequestContext.set("req-aspect-1");
    }

    @AfterEach
    void cleanup() {
        UserContext.clear();
        RequestContext.clear();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void successPath_capturesHttpMetadata_fromRequest() {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/demo");
        req.addHeader("X-Forwarded-For", "203.0.113.7, 10.0.0.1");
        req.addHeader("User-Agent", "JUnit-UA");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));

        proxy.create(new Payload("Alice"));

        AuditEvent e = recorder.events.get(0);
        assertThat(e.method()).isEqualTo("POST");
        assertThat(e.path()).isEqualTo("/api/v1/demo");
        assertThat(e.ip()).isEqualTo("203.0.113.7"); // X-Forwarded-For 取首跳
        assertThat(e.userAgent()).isEqualTo("JUnit-UA");
    }

    @Test
    void noRequestContext_httpMetadataNull() {
        // 无 ServletRequestAttributes（MQ/@Async 线程）：HTTP 元信息为 null，不报错
        proxy.create(new Payload("Bob"));
        AuditEvent e = recorder.events.get(0);
        assertThat(e.method()).isNull();
        assertThat(e.path()).isNull();
        assertThat(e.ip()).isNull();
        assertThat(e.userAgent()).isNull();
    }

    @Test
    void successPath_recordsOneEvent_withSpELResolved() {
        Result r = proxy.create(new Payload("Alice"));
        assertThat(r.id()).isEqualTo(42L);
        assertThat(recorder.events).hasSize(1);
        AuditEvent e = recorder.events.get(0);
        assertThat(e.action()).isEqualTo("create");
        assertThat(e.module()).isEqualTo("demo");
        assertThat(e.objectType()).isEqualTo("Demo");
        assertThat(e.objectId()).isEqualTo("42");
        assertThat(e.objectDesc()).isEqualTo("Alice");
        assertThat(e.requestId()).isEqualTo("req-aspect-1");
        assertThat(e.tenantId()).isEqualTo(99L);
        assertThat(e.operatorId()).isEqualTo(1001L);
        assertThat(e.operatorDeptId()).isEqualTo(5001L);
        assertThat(e.statusCode()).isEqualTo(200);
    }

    @Test
    void operatorName_prefersPrincipalRealName_withoutDbLookup() {
        // 登录态已带 realName → 直接读内存取姓名，不查库（noopUserDirectory 返回 null 也不影响）
        UserContext.set(new UserPrincipal(1001L, 99L, "tester", "sid-x", false, 5001L,
                false, null, "测试员"));
        proxy.create(new Payload("Alice"));
        assertThat(recorder.events.get(0).operatorName()).isEqualTo("测试员");
    }

    @Test
    void operatorName_fallsBackToUsername_whenPrincipalRealNameNull() {
        // principal 未带 realName（合成主体）+ 查库无果 → 回退账号
        proxy.create(new Payload("Alice"));
        assertThat(recorder.events.get(0).operatorName()).isEqualTo("tester");
    }

    @Test
    void exceptionPath_noRequestContext_recordsInlineWith500() {
        // 非 Web 线程(MQ/@Async)：无 servlet 上下文，失败事件内联兜底记 500
        assertThatThrownBy(() -> proxy.failing(new Payload("Bob")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");
        assertThat(recorder.events).hasSize(1);
        AuditEvent e = recorder.events.get(0);
        assertThat(e.statusCode()).isEqualTo(500);
        assertThat(e.errorMessage()).isEqualTo("boom");
    }

    @Test
    void exceptionPath_inWebRequest_stashesInsteadOfInlineRecord() {
        // Web 请求：失败时最终 HTTP 状态未定，应暂存到请求（不内联 record），由 flush filter 回填后落库
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/demo");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));

        assertThatThrownBy(() -> proxy.failing(new Payload("Bob")))
                .isInstanceOf(IllegalStateException.class);

        assertThat(recorder.events).isEmpty(); // 未内联记录
        List<AuditEvent> pending = PendingFailureAudits.drain(req);
        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).errorMessage()).isEqualTo("boom");
    }

    @Test
    void simpleEndpoint_recordsEvent() {
        // 字段级 diff 归 ③；② 只记一条事件（谁/动作/对象/信封）
        proxy.noBefore("x");
        assertThat(recorder.events).hasSize(1);
        assertThat(recorder.events.get(0).action()).isEqualTo("noBefore");
    }

    @Test
    void spelFailure_doesNotBreakBusiness() {
        proxy.badSpel(new Payload("Z"));
        assertThat(recorder.events).hasSize(1);
        assertThat(recorder.events.get(0).objectId()).isNull();
    }

    @Test
    void noArgs_doesNotThrow() {
        proxy.noArgs();
        assertThat(recorder.events).hasSize(1);
        assertThat(recorder.events.get(0).action()).isEqualTo("ping");
    }

    @Test
    void recorderThrows_doesNotBreakBusiness() {
        proxy = new AspectJProxyFactory(new DemoService()).getProxy();
        // re-wire with throwing recorder
        AspectJProxyFactory factory = new AspectJProxyFactory(new DemoService());
        factory.addAspect(new AuditableAspect(new ThrowingRecorder(), noopUserDirectory()));
        DemoService p2 = factory.getProxy();
        Result r = p2.create(new Payload("ok"));
        assertThat(r.id()).isEqualTo(42L);
    }

    static class DemoService {
        @Auditable(module = "demo", action = "create", objectType = "Demo",
                objectIdExpr = "#result.id", objectDescExpr = "#result.name")
        public Result create(Payload payload) {
            return new Result(42L, payload.name());
        }

        @Auditable(module = "demo", action = "update", objectType = "Demo",
                objectIdExpr = "#payload.name")
        public void failing(Payload payload) {
            throw new IllegalStateException("boom");
        }

        @Auditable(module = "demo", action = "noBefore", objectType = "Demo")
        public Result noBefore(String x) {
            return new Result(0L, x);
        }

        @Auditable(module = "demo", action = "badSpel", objectType = "Demo",
                objectIdExpr = "#nonexistent.field.something")
        public Result badSpel(Payload payload) {
            return new Result(1L, payload.name());
        }

        @Auditable(module = "demo", action = "ping", objectType = "Demo")
        public void noArgs() {
        }
    }

    record Payload(String name) {}
    record Result(long id, String name) {}

    /** 操作人姓名查库的桩：displayName 恒返回 null → aspect 回退 principal.username()，不依赖 DB。 */
    private static UserQueryService noopUserDirectory() {
        return org.mockito.Mockito.mock(UserQueryService.class);
    }

    static class RecordingRecorder implements AuditLogRecorder {
        final List<AuditEvent> events = new ArrayList<>();
        @Override public void record(AuditEvent event) { events.add(event); }
        @Override public void recordCreate(String m, String t, String i, String d, Object a) {}
        @Override public void recordUpdate(String m, String t, String i, String d, Object b, Object a) {}
        @Override public void recordDelete(String m, String t, String i, String d, Object b) {}
        @Override public void recordLogin(LoginAuditEvent event) {}
    }

    static class ThrowingRecorder implements AuditLogRecorder {
        @Override public void record(AuditEvent event) { throw new RuntimeException("recorder dead"); }
        @Override public void recordCreate(String m, String t, String i, String d, Object a) {}
        @Override public void recordUpdate(String m, String t, String i, String d, Object b, Object a) {}
        @Override public void recordDelete(String m, String t, String i, String d, Object b) {}
        @Override public void recordLogin(LoginAuditEvent event) {}
    }
}
