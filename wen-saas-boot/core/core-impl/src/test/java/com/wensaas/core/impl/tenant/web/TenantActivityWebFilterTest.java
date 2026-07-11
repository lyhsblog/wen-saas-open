package com.wensaas.core.impl.tenant.web;

import com.wensaas.core.api.tenant.TenantContext;
import com.wensaas.core.impl.auth.filter.AuthPathMatcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TenantActivityWebFilterTest {

    private final TenantActivityGuard guard = mock(TenantActivityGuard.class);
    private final AuthPathMatcher matcher = mock(AuthPathMatcher.class);
    private final TenantActivityWebFilter filter = new TenantActivityWebFilter(guard, matcher);

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    void publicEndpoint_skipsGuard_evenWhenTenantContextSet() throws Exception {
        // 回归：dev 下前端残留 X-Tenant-Id 会把公开按-token 设密码请求设上租户上下文，
        // 公开端点必须豁免写守卫，否则被误判为「租户已到期，禁止写操作」。
        when(matcher.isPublic(any())).thenReturn(true);
        TenantContext.set(9999L);

        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(
                new MockHttpServletRequest("POST", "/api/v1/public/reset-password/by-token/x"),
                new MockHttpServletResponse(), chain);

        verify(guard, never()).requireActive(anyLong());
        assertThat(chain.getRequest()).isNotNull(); // 已放行至下游
    }

    @Test
    void nonPublicWrite_invokesGuard() throws Exception {
        when(matcher.isPublic(any())).thenReturn(false);
        TenantContext.set(9999L);

        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(
                new MockHttpServletRequest("POST", "/api/v1/ops/tenants/1/plans"),
                new MockHttpServletResponse(), chain);

        verify(guard).requireActive(9999L);
    }

    @Test
    void readMethod_skipsGuard() throws Exception {
        when(matcher.isPublic(any())).thenReturn(false);
        TenantContext.set(9999L);

        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(
                new MockHttpServletRequest("GET", "/api/v1/ops/tenants"),
                new MockHttpServletResponse(), chain);

        verify(guard, never()).requireActive(anyLong());
    }
}
