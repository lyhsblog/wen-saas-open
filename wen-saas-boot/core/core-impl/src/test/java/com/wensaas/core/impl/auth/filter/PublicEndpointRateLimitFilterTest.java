package com.wensaas.core.impl.auth.filter;

import com.wensaas.core.impl.auth.filter.PublicEndpointRateLimitFilter;
import com.wensaas.core.impl.auth.filter.PublicRateLimitProperties;
import com.wensaas.core.impl.coordination.SlidingWindowRateLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublicEndpointRateLimitFilterTest {

    private final SlidingWindowRateLimiter limiter = mock(SlidingWindowRateLimiter.class);
    private final PublicRateLimitProperties props = new PublicRateLimitProperties();
    private final PublicEndpointRateLimitFilter filter = new PublicEndpointRateLimitFilter(limiter, props);

    private long reset() {
        return Instant.now().getEpochSecond() + 60;
    }

    @Test
    void nonMatchingPath_skipsLimiter() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/tenants");
        assertThat(filter.shouldNotFilter(req)).isTrue();
    }

    @Test
    void disabled_skipsLimiter() {
        props.setEnabled(false);
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/oauth/dingtalk/login");
        assertThat(filter.shouldNotFilter(req)).isTrue();
    }

    @Test
    void matchingPaths_areFiltered() {
        assertThat(filter.shouldNotFilter(
                new MockHttpServletRequest("POST", "/api/auth/oauth/dingtalk/login"))).isFalse();
        assertThat(filter.shouldNotFilter(
                new MockHttpServletRequest("GET", "/api/v1/public/reset-password/by-token/abc"))).isFalse();
    }

    @Test
    void allowed_passesThroughChainAndSetsHeaders() throws Exception {
        when(limiter.tryAcquire(any(), anyInt(), any()))
                .thenReturn(new SlidingWindowRateLimiter.Decision(true, 20, 19, reset()));
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/oauth/dingtalk/login");
        req.setRemoteAddr("1.2.3.4");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, resp, chain);

        assertThat(chain.getRequest()).isNotNull();   // 已放行到下一环
        assertThat(resp.getStatus()).isEqualTo(200);
        assertThat(resp.getHeader("X-RateLimit-Remaining")).isEqualTo("19");
    }

    @Test
    void exceeded_returns429AndStopsChain() throws Exception {
        when(limiter.tryAcquire(any(), anyInt(), any()))
                .thenReturn(new SlidingWindowRateLimiter.Decision(false, 20, 0, reset()));
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/oauth/dingtalk/login");
        req.setRemoteAddr("1.2.3.4");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, resp, chain);

        assertThat(chain.getRequest()).isNull();       // 未放行
        assertThat(resp.getStatus()).isEqualTo(429);
        assertThat(resp.getHeader("Retry-After")).isNotNull();
        assertThat(resp.getContentAsString()).contains("Request rate limit exceeded");
    }

    @Test
    void clientIp_prefersXForwardedForFirstHop() throws Exception {
        when(limiter.tryAcquire(eq(props.getKeyPrefix() + ":9.9.9.9"), anyInt(), any()))
                .thenReturn(new SlidingWindowRateLimiter.Decision(true, 20, 19, reset()));
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/oauth/dingtalk/login");
        req.setRemoteAddr("10.0.0.1");
        req.addHeader("X-Forwarded-For", "9.9.9.9, 10.0.0.1");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilter(req, resp, new MockFilterChain());

        verify(limiter).tryAcquire(eq(props.getKeyPrefix() + ":9.9.9.9"), eq(20), any(Duration.class));
        verify(limiter, never()).tryAcquire(eq(props.getKeyPrefix() + ":10.0.0.1"), anyInt(), any());
    }
}
