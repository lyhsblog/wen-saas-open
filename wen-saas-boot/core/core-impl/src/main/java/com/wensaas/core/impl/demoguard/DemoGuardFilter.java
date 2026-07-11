package com.wensaas.core.impl.demoguard;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 演示守卫：命中「方法 + Ant 路径」黑名单的请求直接返回 403，不进业务链路。
 *
 * <p>注册在 {@code AuthenticationFilter}（+50）之前（+45）：拦截不依赖认证身份，
 * 对所有请求方一视同仁，最早短路。规则见 {@link DemoGuardProperties#getRules()}。
 */
public class DemoGuardFilter extends OncePerRequestFilter {

    private static final Set<String> HTTP_METHODS = Set.of(
            "GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS");

    /** 单条规则：方法集合 + Ant 路径模式。 */
    record Rule(Set<String> methods, String pattern) { }

    private final List<Rule> rules;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public DemoGuardFilter(List<String> ruleSpecs) {
        this.rules = parseRules(ruleSpecs);
    }

    /**
     * 解析 {@code "METHOD[,METHOD] antPattern"} 规则串；格式非法抛出，装配期 fail-fast。
     */
    static List<Rule> parseRules(List<String> specs) {
        List<Rule> out = new ArrayList<>();
        for (String spec : specs == null ? List.<String>of() : specs) {
            if (spec == null || spec.isBlank()) {
                continue;
            }
            // 末段 = 路径模式，其余 = 方法列表（容忍逗号前后空格，如 "PUT, DELETE /xx"）
            String[] tokens = spec.trim().split("\\s+");
            if (tokens.length < 2) {
                throw new IllegalArgumentException(
                        "demo-guard 规则格式应为 \"METHOD[,METHOD] antPattern\"，收到：" + spec);
            }
            String pattern = tokens[tokens.length - 1];
            if (!pattern.startsWith("/")) {
                throw new IllegalArgumentException(
                        "demo-guard 规则路径必须以 / 开头，收到：" + spec);
            }
            Set<String> methods = new LinkedHashSet<>();
            String methodsPart = String.join("", Arrays.copyOf(tokens, tokens.length - 1));
            for (String m : methodsPart.split(",")) {
                if (m.isBlank()) continue;
                String method = m.trim().toUpperCase(Locale.ROOT);
                if (!HTTP_METHODS.contains(method)) {
                    throw new IllegalArgumentException(
                            "demo-guard 规则含未知 HTTP 方法 \"" + m + "\"：" + spec);
                }
                methods.add(method);
            }
            if (methods.isEmpty()) {
                throw new IllegalArgumentException(
                        "demo-guard 规则缺少 HTTP 方法：" + spec);
            }
            out.add(new Rule(Set.copyOf(methods), pattern));
        }
        return List.copyOf(out);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain chain) throws ServletException, IOException {
        String method = request.getMethod().toUpperCase(Locale.ROOT);
        String uri = request.getRequestURI();
        for (Rule rule : rules) {
            if (rule.methods().contains(method) && pathMatcher.match(rule.pattern(), uri)) {
                writeForbidden(response);
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private static void writeForbidden(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
                "{\"status\":403,\"title\":\"Demo guard forbidden\","
                + "\"detail\":\"演示环境已禁用该操作\","
                + "\"type\":\"https://wen-saas-boot/errors/demo-guard/forbidden\","
                + "\"code\":\"DEMO_GUARD_FORBIDDEN\"}");
    }
}
