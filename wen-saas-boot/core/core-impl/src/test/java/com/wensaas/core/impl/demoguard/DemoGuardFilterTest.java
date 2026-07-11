package com.wensaas.core.impl.demoguard;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DemoGuardFilterTest {

    private static final List<String> DEMO_RULES = List.of(
            "POST,PUT,DELETE /api/v1/ops/menus/**",
            "PUT,DELETE /api/v1/ops/team/members/**",
            "POST /api/v1/ops/team/members/*/**",
            "PUT /api/v1/ops/settings/security_policy");

    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain chain;
    private StringWriter body;

    @BeforeEach
    void setup() throws Exception {
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        chain = mock(FilterChain.class);
        body = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(body));
    }

    // ===== 规则解析 =====

    @Test
    void parse_multiMethodRule_ok() {
        List<DemoGuardFilter.Rule> rules = DemoGuardFilter.parseRules(
                List.of("put, delete /api/v1/ops/team/roles/**"));
        assertThat(rules).hasSize(1);
        assertThat(rules.getFirst().methods()).containsExactlyInAnyOrder("PUT", "DELETE");
        assertThat(rules.getFirst().pattern()).isEqualTo("/api/v1/ops/team/roles/**");
    }

    @Test
    void parse_blankEntries_skipped() {
        assertThat(DemoGuardFilter.parseRules(List.of("  ", ""))).isEmpty();
        assertThat(DemoGuardFilter.parseRules(null)).isEmpty();
    }

    @Test
    void parse_missingPattern_failsFast() {
        assertThatThrownBy(() -> DemoGuardFilter.parseRules(List.of("POST")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parse_unknownMethod_failsFast() {
        assertThatThrownBy(() -> DemoGuardFilter.parseRules(List.of("FETCH /api/v1/ops/menus/**")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parse_patternWithoutLeadingSlash_failsFast() {
        assertThatThrownBy(() -> DemoGuardFilter.parseRules(List.of("POST api/v1/ops/menus/**")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ===== 命中 / 放行 =====

    @Test
    void blockedMethodAndPath_returns403_notChained() throws Exception {
        DemoGuardFilter filter = new DemoGuardFilter(DEMO_RULES);
        when(request.getMethod()).thenReturn("DELETE");
        when(request.getRequestURI()).thenReturn("/api/v1/ops/menus/42");

        filter.doFilterInternal(request, response, chain);

        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verify(chain, never()).doFilter(request, response);
        assertThat(body.toString()).contains("DEMO_GUARD_FORBIDDEN").contains("演示环境已禁用该操作");
    }

    @Test
    void getOnBlockedPath_passes() throws Exception {
        DemoGuardFilter filter = new DemoGuardFilter(DEMO_RULES);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/v1/ops/menus");

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void createMember_exactPathPost_passes_subPathPost_blocked() throws Exception {
        DemoGuardFilter filter = new DemoGuardFilter(DEMO_RULES);

        // POST 精确路径（新建成员）放行：/*/** 至少要求一段子路径
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/v1/ops/team/members");
        filter.doFilterInternal(request, response, chain);
        verify(chain).doFilter(request, response);

        // 子路径 POST（重置密码）拦截
        when(request.getRequestURI()).thenReturn("/api/v1/ops/team/members/7/reset-password");
        filter.doFilterInternal(request, response, chain);
        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
    }

    @Test
    void settingsOnlySecurityPolicyBlocked() throws Exception {
        DemoGuardFilter filter = new DemoGuardFilter(DEMO_RULES);
        when(request.getMethod()).thenReturn("PUT");

        when(request.getRequestURI()).thenReturn("/api/v1/ops/settings/platform_info");
        filter.doFilterInternal(request, response, chain);
        verify(chain).doFilter(request, response);

        when(request.getRequestURI()).thenReturn("/api/v1/ops/settings/security_policy");
        filter.doFilterInternal(request, response, chain);
        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
    }
}
