package com.wensaas.core.impl.tenant.web;

import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.auth.user.UserPrincipal;
import com.wensaas.core.api.tenant.TenantContext;
import com.wensaas.core.impl.auth.AuthProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Web 层租户上下文过滤器。
 *
 * <p>来源优先级：
 * <ol>
 *   <li>{@link UserContext} 中的 {@link UserPrincipal#tenantId()}（由前置 AuthenticationFilter 注入）</li>
 *   <li>仅在 {@link AuthProperties#isAllowTenantIdHeader()} 为 true 时，降级解析 {@code X-Tenant-Id} header
 *       （dev / 内部健康探针场景）</li>
 *   <li>都没有时不设置；下游业务调用 {@link TenantContext#require()} 会抛出</li>
 * </ol>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public class TenantContextWebFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TenantContextWebFilter.class);

    /** HTTP header 名（仅在 allow-tenant-id-header=true 时使用）。 */
    public static final String TENANT_ID_HEADER = "X-Tenant-Id";

    private final AuthProperties authProperties;

    public TenantContextWebFilter(AuthProperties authProperties) {
        this.authProperties = authProperties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain chain) throws ServletException, IOException {
        Long tenantId = resolveTenantId(request);
        boolean set = false;
        if (tenantId != null) {
            TenantContext.set(tenantId);
            set = true;
        }
        try {
            chain.doFilter(request, response);
        } finally {
            if (set) {
                TenantContext.clear();
            }
        }
    }

    Long resolveTenantId(HttpServletRequest request) {
        UserPrincipal principal = UserContext.get();
        if (principal != null) {
            return principal.tenantId();
        }
        if (authProperties.isAllowTenantIdHeader()) {
            return parseHeader(request.getHeader(TENANT_ID_HEADER));
        }
        return null;
    }

    private static Long parseHeader(String headerValue) {
        // fail-close：空值 / 非法值<b>不再</b>回退 PLATFORM_TENANT_ID=0，而是返回 null
        // （= 不设置租户上下文，下游 TenantContext.require() 会抛）。避免「漏传/传错 header → 静默落到平台租户」。
        // 需要平台上下文请显式传 "0"。
        if (headerValue == null || headerValue.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(headerValue.trim());
        } catch (NumberFormatException e) {
            log.warn("非法 {} header 值 '{}'，fail-close 不设置租户上下文", TENANT_ID_HEADER, headerValue);
            return null;
        }
    }
}
