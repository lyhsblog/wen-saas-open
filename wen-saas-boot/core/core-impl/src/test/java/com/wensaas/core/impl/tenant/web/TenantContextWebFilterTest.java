package com.wensaas.core.impl.tenant.web;

import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.auth.user.UserPrincipal;
import com.wensaas.core.api.tenant.TenantContext;
import com.wensaas.core.impl.auth.AuthProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TenantContextWebFilterTest {

    @AfterEach
    void cleanup() {
        TenantContext.clear();
        UserContext.clear();
    }

    private TenantContextWebFilter newFilter(boolean allowHeader) {
        AuthProperties props = new AuthProperties();
        props.setAllowTenantIdHeader(allowHeader);
        return new TenantContextWebFilter(props);
    }

    @Test
    void resolveTenantId_fromUserContext_takesPriority() {
        TenantContextWebFilter filter = newFilter(true);
        UserContext.set(new UserPrincipal(1L, 42L, "u", "sid", false, null, false, null));
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader(TenantContextWebFilter.TENANT_ID_HEADER)).thenReturn("99");

        assertThat(filter.resolveTenantId(req)).isEqualTo(42L);
    }

    @Test
    void resolveTenantId_noUserContext_headerAllowed_usesHeader() {
        TenantContextWebFilter filter = newFilter(true);
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader(TenantContextWebFilter.TENANT_ID_HEADER)).thenReturn("7");

        assertThat(filter.resolveTenantId(req)).isEqualTo(7L);
    }

    @Test
    void resolveTenantId_noUserContext_headerAllowed_missingHeader_failsClose() {
        // M3 fail-close：空 header 不再回退 PLATFORM_TENANT_ID=0，返回 null（不设置上下文）。
        TenantContextWebFilter filter = newFilter(true);
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader(TenantContextWebFilter.TENANT_ID_HEADER)).thenReturn(null);

        assertThat(filter.resolveTenantId(req)).isNull();
    }

    @Test
    void resolveTenantId_noUserContext_headerAllowed_invalidHeader_failsClose() {
        // M3 fail-close：非法 header 值不再回退 PLATFORM_TENANT_ID=0，返回 null。
        TenantContextWebFilter filter = newFilter(true);
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader(TenantContextWebFilter.TENANT_ID_HEADER)).thenReturn("not-a-number");

        assertThat(filter.resolveTenantId(req)).isNull();
    }

    @Test
    void resolveTenantId_noUserContext_headerAllowed_explicitZero_resolvesPlatform() {
        // 仍可显式传 "0" 取平台上下文。
        TenantContextWebFilter filter = newFilter(true);
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader(TenantContextWebFilter.TENANT_ID_HEADER)).thenReturn("0");

        assertThat(filter.resolveTenantId(req)).isEqualTo(TenantContext.PLATFORM_TENANT_ID);
    }

    @Test
    void resolveTenantId_noUserContext_headerDisallowed_returnsNull() {
        TenantContextWebFilter filter = newFilter(false);
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader(TenantContextWebFilter.TENANT_ID_HEADER)).thenReturn("99");

        assertThat(filter.resolveTenantId(req)).isNull();
    }

    @Test
    void doFilter_setsAndClearsContextAroundChain_fromUserContext() throws Exception {
        TenantContextWebFilter filter = newFilter(false);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        UserContext.set(new UserPrincipal(1L, 88L, "u", "sid", false, null, false, null));

        doAnswer(inv -> {
            assertThat(TenantContext.get()).isEqualTo(88L);
            return null;
        }).when(chain).doFilter(any(), any());

        filter.doFilter(request, response, chain);

        assertThat(TenantContext.get()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void doFilter_clearsContextOnException() throws Exception {
        TenantContextWebFilter filter = newFilter(true);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getHeader(TenantContextWebFilter.TENANT_ID_HEADER)).thenReturn("5");
        doThrow(new RuntimeException("boom")).when(chain).doFilter(any(), any());

        try {
            filter.doFilter(request, response, chain);
        } catch (RuntimeException ignored) {
        }

        assertThat(TenantContext.get()).isNull();
    }

    @Test
    void doFilter_noUserContextAndHeaderDisallowed_doesNotPolluteThreadLocal() throws Exception {
        TenantContextWebFilter filter = newFilter(false);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        doAnswer(inv -> {
            assertThat(TenantContext.get()).isNull();
            return null;
        }).when(chain).doFilter(any(), any());

        filter.doFilter(request, response, chain);

        assertThat(TenantContext.get()).isNull();
    }
}
