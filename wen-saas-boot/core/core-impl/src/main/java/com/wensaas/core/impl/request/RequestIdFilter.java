package com.wensaas.core.impl.request;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.wensaas.core.api.request.RequestContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * Request ID 过滤器：链路最前。
 *
 * <p>读 {@code X-Request-Id}（缺失或非法时 fallback 雪花 ID）→ 写
 * {@link RequestContext}、MDC（key={@code requestId}）、Response Header → finally 清理。
 *
 * <p>{@link Order} 用 {@link Ordered#HIGHEST_PRECEDENCE}；项目内
 * {@code AuthenticationFilter} 之前定为 {@code HIGHEST_PRECEDENCE}，本次同步上移到
 * {@code HIGHEST_PRECEDENCE + 50} 让位（避免 {@code HIGHEST_PRECEDENCE - 100} 整数下溢的陷阱）。
 *
 * <p>MDC 占位已在 {@code logback-spring.xml} 预留：{@code %X{requestId:-}}。
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    private static final int MAX_LEN = 64;
    private static final Pattern ALLOWED = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                     HttpServletResponse resp,
                                     FilterChain chain) throws ServletException, IOException {
        String id = sanitize(req.getHeader(HEADER));
        if (id == null) {
            id = String.valueOf(IdWorker.getId());
        }
        RequestContext.set(id);
        MDC.put(MDC_KEY, id);
        resp.setHeader(HEADER, id);
        try {
            chain.doFilter(req, resp);
        } finally {
            MDC.remove(MDC_KEY);
            RequestContext.clear();
            com.wensaas.core.api.audit.AuditObjects.clear();
        }
    }

    /** 仅允许 {@code [A-Za-z0-9_-]}、长度 ≤ 64；不合法返回 null（由调用方走自生成）。 */
    static String sanitize(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_LEN) {
            return null;
        }
        return ALLOWED.matcher(trimmed).matches() ? trimmed : null;
    }
}
