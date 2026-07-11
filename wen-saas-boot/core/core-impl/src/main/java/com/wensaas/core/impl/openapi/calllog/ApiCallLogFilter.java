package com.wensaas.core.impl.openapi.calllog;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.auth.user.UserPrincipal;
import com.wensaas.core.api.request.RequestContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import com.wensaas.core.impl.openapi.OpenApiProperties;
import com.wensaas.core.impl.openapi.gateway.AppKeyAuthenticationFilter;

/**
 * 开放 API 调用日志 Filter。
 *
 * <p>顺序：{@code HIGHEST_PRECEDENCE + 30}，位于 {@link AppKeyAuthenticationFilter}（+100）之前，
 * 这样无论鉴权成功 / 失败都能记录调用。鉴权 Filter 在 chain.doFilter 之前注入 UserContext，
 * 本 Filter 在 chain.doFilter 之后从 UserContext 取最终 principal（鉴权失败时为 null）。
 *
 * <p>仅作用于 {@code wen-saas-boot.openapi.path-prefix} 路径；其他请求由 audit v1 的 AuditableAspect 记录。
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 30)
public class ApiCallLogFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiCallLogFilter.class);
    private static final int ERROR_MSG_MAX = 1024;

    private final ApiCallEventPublisher publisher;
    private final OpenApiProperties props;
    private final com.wensaas.core.impl.pkg.ModuleApiRouteRegistry routeRegistry;

    public ApiCallLogFilter(ApiCallEventPublisher publisher, OpenApiProperties props,
                            com.wensaas.core.impl.pkg.ModuleApiRouteRegistry routeRegistry) {
        this.publisher = publisher;
        this.props = props;
        this.routeRegistry = routeRegistry;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        String prefix = props.getPathPrefix();
        return prefix == null || prefix.isBlank() || !path.startsWith(prefix);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain chain) throws ServletException, IOException {
        long startNanos = System.nanoTime();
        String errorMessage = null;
        try {
            chain.doFilter(request, response);
        } catch (RuntimeException | ServletException | IOException ex) {
            errorMessage = truncate(ex.toString(), ERROR_MSG_MAX);
            throw ex;
        } finally {
            try {
                publishEvent(request, response, startNanos, errorMessage);
            } catch (RuntimeException pubEx) {
                log.warn("apicall.publish.in-filter.fail reason={}", pubEx.getMessage());
            }
        }
    }

    private void publishEvent(HttpServletRequest request, HttpServletResponse response,
                              long startNanos, String errorMessage) {
        long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;
        // 优先读 request attribute（AppKeyAuthFilter 鉴权成功时写入，本 Filter 在 finally 读取时
        // ThreadLocal UserContext 已被 inner filter clear；attribute 在 request 生命周期内有效）
        UserPrincipal principal = (UserPrincipal) request.getAttribute(AppKeyAuthenticationFilter.ATTR_PRINCIPAL);
        if (principal == null) {
            principal = UserContext.get();
        }
        Long apiKeyId = principal != null && principal.byApiKey() ? principal.apiKeyId() : null;
        Long tenantId = principal != null ? principal.tenantId() : 0L;
        Integer requestSize = request.getContentLength() >= 0 ? request.getContentLength() : null;

        ApiCallEvent event = new ApiCallEvent(
                IdWorker.getId(),
                tenantId,
                apiKeyId,
                null, // apiKeyName v1 暂不冗余（鉴权阶段已校验）
                RequestContext.get(),
                request.getMethod(),
                request.getRequestURI(),
                response.getStatus(),
                (int) durationMs,
                requestSize,
                null, // responseSize v1 不强求（HttpServletResponse 不直接暴露）
                errorMessage,
                resolveClientIp(request),
                request.getHeader("User-Agent"),
                routeRegistry.findModuleCode(request.getRequestURI()).orElse(null),
                Instant.now());
        publisher.publish(event);
    }

    private static String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma < 0 ? xff : xff.substring(0, comma)).trim();
        }
        return request.getRemoteAddr();
    }

    private static String truncate(String s, int max) {
        if (s == null || s.length() <= max) {
            return s;
        }
        return s.substring(0, max);
    }
}
