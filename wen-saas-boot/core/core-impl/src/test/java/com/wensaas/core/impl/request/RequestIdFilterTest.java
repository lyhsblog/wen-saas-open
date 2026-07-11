package com.wensaas.core.impl.request;

import com.wensaas.core.api.request.RequestContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RequestIdFilterTest {

    @AfterEach
    void cleanup() {
        RequestContext.clear();
        MDC.clear();
    }

    @Test
    void existingHeader_isReused() throws Exception {
        RequestIdFilter filter = new RequestIdFilter();
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(req.getHeader(RequestIdFilter.HEADER)).thenReturn("client-abc-123");

        doAnswer(inv -> {
            assertThat(RequestContext.get()).isEqualTo("client-abc-123");
            assertThat(MDC.get("requestId")).isEqualTo("client-abc-123");
            return null;
        }).when(chain).doFilter(req, resp);

        filter.doFilter(req, resp, chain);

        verify(resp, atLeastOnce()).setHeader(eq(RequestIdFilter.HEADER), eq("client-abc-123"));
        assertThat(RequestContext.get()).isNull();
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void missingHeader_generatesSnowflake() throws Exception {
        RequestIdFilter filter = new RequestIdFilter();
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(req.getHeader(RequestIdFilter.HEADER)).thenReturn(null);

        java.util.concurrent.atomic.AtomicReference<String> seenInChain = new java.util.concurrent.atomic.AtomicReference<>();
        doAnswer(inv -> {
            seenInChain.set(RequestContext.get());
            return null;
        }).when(chain).doFilter(req, resp);

        filter.doFilter(req, resp, chain);

        assertThat(seenInChain.get()).isNotNull().isNotBlank();
        assertThat(seenInChain.get()).matches("\\d+"); // 雪花 ID 纯数字
        assertThat(RequestContext.get()).isNull();
    }

    @Test
    void illegalCharacterHeader_falsbackToGenerated() throws Exception {
        RequestIdFilter filter = new RequestIdFilter();
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(req.getHeader(RequestIdFilter.HEADER)).thenReturn("bad spaces!");

        java.util.concurrent.atomic.AtomicReference<String> seen = new java.util.concurrent.atomic.AtomicReference<>();
        doAnswer(inv -> {
            seen.set(RequestContext.get());
            return null;
        }).when(chain).doFilter(req, resp);

        filter.doFilter(req, resp, chain);

        assertThat(seen.get()).matches("\\d+");
    }

    @Test
    void overlongHeader_isRejected() {
        String longer = "a".repeat(65);
        assertThat(RequestIdFilter.sanitize(longer)).isNull();
    }

    @Test
    void chainThrows_stillClearsMDCAndContext() throws Exception {
        RequestIdFilter filter = new RequestIdFilter();
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(req.getHeader(RequestIdFilter.HEADER)).thenReturn("rid-1");
        doThrow(new RuntimeException("boom")).when(chain).doFilter(req, resp);

        assertThatThrownBy(() -> filter.doFilter(req, resp, chain))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("boom");

        assertThat(RequestContext.get()).isNull();
        assertThat(MDC.get("requestId")).isNull();
        verify(resp, times(1)).setHeader(eq(RequestIdFilter.HEADER), eq("rid-1"));
    }
}
