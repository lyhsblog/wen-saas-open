package com.wensaas.core.impl.audit.pending;

import com.wensaas.core.api.audit.AuditEvent;
import com.wensaas.core.api.audit.AuditLogRecorder;
import com.wensaas.core.api.audit.LoginAuditEvent;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PendingAuditFlushFilterTest {

    private static AuditEvent failureEvent() {
        // 模拟切面暂存的失败事件：占位状态 500，待 filter 回填真实状态
        return new AuditEvent(null, 0L, null, "req-1", 1L, "t", 1L, "ops", "新建", "运营成员",
                null, null, "POST", "/api/v1/ops/team/members", 500, 3, "用户名已存在",
                "127.0.0.1", "JUnit", Instant.now());
    }

    @Test
    void flushesPendingFailureWithRealResponseStatus() throws Exception {
        RecordingRecorder recorder = new RecordingRecorder();
        PendingAuditFlushFilter filter = new PendingAuditFlushFilter(recorder);

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/ops/team/members");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        // 预置切面已暂存的失败事件（占位 500）；chain 模拟 advice 把响应状态写成 409
        req.setAttribute(PendingFailureAudits.ATTR, new ArrayList<>(List.of(failureEvent())));
        FilterChain chain = (q, s) -> ((MockHttpServletResponse) s).setStatus(409);

        filter.doFilter(req, resp, chain);

        assertThat(recorder.events).hasSize(1);
        // 状态被回填为响应真实状态 409，而非占位 500
        assertThat(recorder.events.get(0).statusCode()).isEqualTo(409);
        assertThat(recorder.events.get(0).errorMessage()).isEqualTo("用户名已存在");
        // 已清空，避免重复落库
        assertThat(PendingFailureAudits.drain(req)).isEmpty();
    }

    @Test
    void noPending_noRecord() throws Exception {
        RecordingRecorder recorder = new RecordingRecorder();
        PendingAuditFlushFilter filter = new PendingAuditFlushFilter(recorder);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/ops/team/members");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilter(req, resp, (q, s) -> ((MockHttpServletResponse) s).setStatus(200));

        assertThat(recorder.events).isEmpty();
    }

    static class RecordingRecorder implements AuditLogRecorder {
        final List<AuditEvent> events = new ArrayList<>();
        @Override public void record(AuditEvent event) { events.add(event); }
        @Override public void recordCreate(String m, String t, String i, String d, Object a) {}
        @Override public void recordUpdate(String m, String t, String i, String d, Object b, Object a) {}
        @Override public void recordDelete(String m, String t, String i, String d, Object b) {}
        @Override public void recordLogin(LoginAuditEvent event) {}
    }
}
