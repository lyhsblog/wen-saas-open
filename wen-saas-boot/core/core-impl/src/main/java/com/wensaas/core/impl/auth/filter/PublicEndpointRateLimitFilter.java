package com.wensaas.core.impl.auth.filter;

import com.alibaba.fastjson2.JSON;
import com.wensaas.core.impl.coordination.SlidingWindowRateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 公开端点 IP 级频控 Filter（架构审查低优项）。
 *
 * <p>顺序 {@code HIGHEST_PRECEDENCE + 40}：在 {@code RequestIdFilter}（{@code 0}）之后、
 * {@code AuthenticationFilter}（{@code +50}）之前，对配置的公开路径按客户端 IP 限流，超限即 429，
 * 不进入后续认证 / 业务处理。
 *
 * <p>仅作用于 {@link PublicRateLimitProperties#getPaths()} 命中的路径；其它请求直接放行。
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 40)
public class PublicEndpointRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(PublicEndpointRateLimitFilter.class);

    private final SlidingWindowRateLimiter rateLimiter;
    private final PublicRateLimitProperties props;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public PublicEndpointRateLimitFilter(SlidingWindowRateLimiter rateLimiter,
                                         PublicRateLimitProperties props) {
        this.rateLimiter = rateLimiter;
        this.props = props;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!props.isEnabled()) {
            return true;
        }
        String path = request.getRequestURI();
        for (String pattern : props.getPaths()) {
            if (pathMatcher.match(pattern, path)) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain chain) throws ServletException, IOException {
        String clientIp = resolveClientIp(request);
        String key = props.getKeyPrefix() + ":" + clientIp;
        SlidingWindowRateLimiter.Decision decision =
                rateLimiter.tryAcquire(key, props.getLimit(), props.getWindow());
        response.setHeader("X-RateLimit-Limit", String.valueOf(decision.limit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, decision.remaining())));
        response.setHeader("X-RateLimit-Reset", String.valueOf(decision.resetEpochSecond()));
        if (!decision.allowed()) {
            long retryAfter = Math.max(1L, decision.resetEpochSecond() - Instant.now().getEpochSecond());
            response.setHeader("Retry-After", String.valueOf(retryAfter));
            log.warn("public.ratelimit.exceeded ip={} path={}", clientIp, request.getRequestURI());
            writeTooManyRequests(response);
            return;
        }
        chain.doFilter(request, response);
    }

    private static String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma < 0 ? xff : xff.substring(0, comma)).trim();
        }
        return request.getRemoteAddr();
    }

    private void writeTooManyRequests(HttpServletResponse response) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>(4);
        body.put("type", "https://wen-saas-boot/errors/rate-limit");
        body.put("title", HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase());
        body.put("status", HttpStatus.TOO_MANY_REQUESTS.value());
        body.put("detail", "Request rate limit exceeded");
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(JSON.toJSONString(body));
    }
}
