package com.wensaas.core.impl.tenant.web;

import com.wensaas.core.api.tenant.TenantActivityException;
import com.wensaas.core.api.tenant.TenantContext;
import com.wensaas.core.impl.auth.filter.AuthPathMatcher;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * 租户活跃度 Web 拦截器。
 *
 * <p>运行于 {@link TenantContextWebFilter}（+100）之后，拦截暂停/到期租户的写请求。
 * 仅对写方法（POST / PUT / PATCH / DELETE）生效；GET / HEAD / OPTIONS 放行。
 * 平台租户（id=0）及未登录（TenantContext 为 null）始终放行。
 *
 * <p>公开端点（{@code @PublicEndpoint} / permit-all，如按 token 设密码、登录）一律放行：
 * 这些接口按凭证/令牌操作、与租户会话无关；dev 下 {@code allow-tenant-id-header=true}
 * 时前端残留的 {@code X-Tenant-Id} 会被 {@link TenantContextWebFilter} 设成租户上下文，
 * 若不豁免则公开写请求会被误判为「租户已到期/暂停」。
 *
 * <p>HTTP 响应：403 Forbidden + {@code application/problem+json}（含 {@code code} 字段）；
 * 由 {@link com.wensaas.core.web.tenant.TenantActivityRestExceptionAdvice} 格式化，
 * 但本 Filter 在 DispatcherServlet 之前运行，MVC ExceptionHandler 不介入，需在此直接写出响应。
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 150)
public class TenantActivityWebFilter extends OncePerRequestFilter {

    private static final Set<String> WRITE_METHODS =
            Set.of(HttpMethod.POST.name(), HttpMethod.PUT.name(),
                   HttpMethod.PATCH.name(), HttpMethod.DELETE.name());

    private final TenantActivityGuard guard;
    private final AuthPathMatcher authPathMatcher;

    public TenantActivityWebFilter(TenantActivityGuard guard, AuthPathMatcher authPathMatcher) {
        this.guard = guard;
        this.authPathMatcher = authPathMatcher;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain chain) throws ServletException, IOException {
        if (!WRITE_METHODS.contains(request.getMethod().toUpperCase())) {
            chain.doFilter(request, response);
            return;
        }
        // 公开端点（按 token / 凭证操作）不受租户活跃写守卫管辖
        if (authPathMatcher.isPublic(request)) {
            chain.doFilter(request, response);
            return;
        }
        Long tenantId = TenantContext.get();
        if (tenantId == null || tenantId == TenantContext.PLATFORM_TENANT_ID) {
            chain.doFilter(request, response);
            return;
        }
        try {
            guard.requireActive(tenantId);
        } catch (TenantActivityException e) {
            writeError(response, e);
            return;
        }
        chain.doFilter(request, response);
    }

    private static void writeError(HttpServletResponse response, TenantActivityException e) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        String code = e.getCode().name();
        String body = "{\"status\":403,\"title\":\"Tenant inactive\","
                + "\"detail\":\"" + e.getMessage() + "\","
                + "\"type\":\"https://wen-saas-boot/errors/tenant/" + code.toLowerCase() + "\","
                + "\"code\":\"" + code + "\"}";
        response.getWriter().write(body);
    }
}
