package com.wensaas.core.impl.request;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * ① 内部 access log。
 *
 * <p>把内部 ops/tenant web 的「修改型请求 + 所有错误响应」连同请求/响应体落到独立日志文件
 * （logger {@code INTERNAL_ACCESS} → {@code logs/access.log}），供内部开发排障；
 * <b>不进 DB、不上 UI、默认不脱敏</b>（内部用，2026-06-08 决策）。requestId 由 MDC（{@code %X{requestId}}）带上。
 *
 * <p>范围：
 * <ul>
 *   <li>记：method ∈ {POST,PUT,PATCH,DELETE} 或 status ≥ 400（任意 method 的错误）；纯成功 GET 不记。</li>
 *   <li>排除：OpenAPI 前缀（其有独立 {@code sys_api_call_log} 双轨）、非 {@code /api/} 路径。</li>
 *   <li>multipart（文件上传）不缓存体，仅记信封，避免缓冲大文件。</li>
 * </ul>
 *
 * <p>位置 {@code HIGHEST_PRECEDENCE+10}：紧随 {@link RequestIdFilter} 之后（requestId 已入 MDC），
 * 在其余 filter/controller 之前包裹请求/响应以缓存体。
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class InternalAccessLogFilter extends OncePerRequestFilter {

    private static final Logger access = LoggerFactory.getLogger("INTERNAL_ACCESS");
    private static final int MAX_BODY = 4096;

    private final String openApiPrefix;

    public InternalAccessLogFilter(String openApiPrefix) {
        this.openApiPrefix = openApiPrefix;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null || !path.startsWith("/api/")) {
            return true; // 静态/非 API 不记
        }
        return openApiPrefix != null && !openApiPrefix.isBlank() && path.startsWith(openApiPrefix);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
            throws ServletException, IOException {
        String ct = req.getContentType();
        boolean multipart = ct != null && ct.toLowerCase().startsWith("multipart/");
        if (multipart) {
            // 不缓存上传体，仅按需记信封（无 body）
            long t0 = System.nanoTime();
            try {
                chain.doFilter(req, resp);
            } finally {
                maybeLog(req, resp.getStatus(), t0, null, null);
            }
            return;
        }

        ContentCachingRequestWrapper reqW = new ContentCachingRequestWrapper(req, MAX_BODY);
        ContentCachingResponseWrapper respW = new ContentCachingResponseWrapper(resp);
        long t0 = System.nanoTime();
        try {
            chain.doFilter(reqW, respW);
        } finally {
            int status = respW.getStatus();
            if (shouldLog(req.getMethod(), status)) {
                maybeLog(req, status, t0,
                        body(reqW.getContentAsByteArray()),
                        textBody(respW));
            }
            respW.copyBodyToResponse(); // 必须：把缓存的响应体写回客户端
        }
    }

    private void maybeLog(HttpServletRequest req, int status, long t0, String reqBody, String respBody) {
        if (!shouldLog(req.getMethod(), status)) {
            return;
        }
        long ms = (System.nanoTime() - t0) / 1_000_000;
        access.info("{} {}{} status={} {}ms req={} resp={}",
                req.getMethod(), req.getRequestURI(), query(req), status, ms,
                reqBody == null ? "-" : reqBody,
                respBody == null ? "-" : respBody);
    }

    private static boolean shouldLog(String method, int status) {
        return status >= 400 || isModifying(method);
    }

    private static boolean isModifying(String method) {
        return "POST".equals(method) || "PUT".equals(method)
                || "PATCH".equals(method) || "DELETE".equals(method);
    }

    private static String query(HttpServletRequest req) {
        String q = req.getQueryString();
        return q == null ? "" : "?" + q;
    }

    /** 仅对 JSON/text 响应取体，避免把二进制（文件下载）塞进日志。 */
    private static String textBody(ContentCachingResponseWrapper resp) {
        String ct = resp.getContentType();
        if (ct == null) {
            return null;
        }
        String lower = ct.toLowerCase();
        if (!lower.contains("json") && !lower.startsWith("text/")) {
            return null;
        }
        return body(resp.getContentAsByteArray());
    }

    /** 应用全程 UTF-8（JSON 规范即 UTF-8），不依赖 servlet 默认 charset（ISO-8859-1 会乱码中文）。 */
    private static String body(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        int len = Math.min(bytes.length, MAX_BODY);
        String s = new String(bytes, 0, len, StandardCharsets.UTF_8).replaceAll("\\s+", " ").trim();
        return bytes.length > MAX_BODY ? s + "...(" + bytes.length + "B)" : s;
    }
}
