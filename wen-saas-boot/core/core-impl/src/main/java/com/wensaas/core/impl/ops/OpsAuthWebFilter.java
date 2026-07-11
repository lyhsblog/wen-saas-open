package com.wensaas.core.impl.ops;

import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.auth.user.UserPrincipal;
import com.wensaas.core.api.tenant.TenantContext;
import com.wensaas.core.impl.auth.filter.AuthenticationFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 运营端专属鉴权拦截器。
 *
 * <p>仅对 {@code /api/v1/ops/} 前缀的路径生效：要求请求方的 JWT 对应 tenant_id=0 的运营账号；
 * 非运营账号一律返回 403 {@code OPS_FORBIDDEN}。
 *
 * <p>运行于 {@link AuthenticationFilter}（+50）、
 * {@link com.wensaas.core.impl.tenant.web.TenantContextWebFilter}（+100）、
 * {@link com.wensaas.core.impl.tenant.web.TenantActivityWebFilter}（+150）之后，
 * 确保 {@link UserContext} 已注入。
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 160)
public class OpsAuthWebFilter extends OncePerRequestFilter {

    private static final String OPS_PATH_PREFIX = "/api/v1/ops/";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain chain) throws ServletException, IOException {
        String uri = request.getRequestURI();
        if (!uri.startsWith(OPS_PATH_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        UserPrincipal ctx = UserContext.get();
        if (ctx == null || ctx.tenantId() != TenantContext.PLATFORM_TENANT_ID) {
            writeForbidden(response);
            return;
        }

        chain.doFilter(request, response);
    }

    private static void writeForbidden(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
                "{\"status\":403,\"title\":\"Ops access forbidden\","
                + "\"detail\":\"仅运营账号（tenant_id=0）可访问此接口\","
                + "\"type\":\"https://wen-saas-boot/errors/ops/forbidden\","
                + "\"code\":\"OPS_FORBIDDEN\"}");
    }
}
