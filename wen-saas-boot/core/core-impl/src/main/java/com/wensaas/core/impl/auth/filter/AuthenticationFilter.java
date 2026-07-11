package com.wensaas.core.impl.auth.filter;

import com.wensaas.core.api.auth.login.AuthenticationException;
import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.auth.user.UserPrincipal;
import com.wensaas.core.impl.auth.session.JwtTokenProvider;
import com.wensaas.core.impl.auth.session.Session;
import com.wensaas.core.impl.auth.session.SessionStore;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.alibaba.fastjson2.JSON;

/**
 * 认证过滤器。
 *
 * <p>顺序：{@code HIGHEST_PRECEDENCE + 50}，紧邻 {@code RequestIdFilter}
 * （{@code HIGHEST_PRECEDENCE}）之后；保证日志 / Audit 能拿到 {@code requestId}。
 *
 * <p>流程：白名单命中 → 放行；否则要求 Bearer JWT → 验签 → 查 Redis Session → 注入
 * {@link UserContext}；任何环节失败统一回 401 ProblemDetail。
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 50)
public class AuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationFilter.class);
    private static final String BEARER = "Bearer ";

    private final AuthPathMatcher pathMatcher;
    private final JwtTokenProvider jwtTokenProvider;
    private final SessionStore sessionStore;

    public AuthenticationFilter(AuthPathMatcher pathMatcher,
                                JwtTokenProvider jwtTokenProvider,
                                SessionStore sessionStore) {
        this.pathMatcher = pathMatcher;
        this.jwtTokenProvider = jwtTokenProvider;
        this.sessionStore = sessionStore;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain chain) throws ServletException, IOException {
        boolean isPublic = pathMatcher.isPublic(request);
        UserPrincipal principal = null;
        try {
            principal = tryAuthenticate(request);
        } catch (AuthenticationException e) {
            if (!isPublic) {
                writeProblem(response, e);
                return;
            }
            // 白名单且 token 异常：忽略 token，按未认证放行
            log.debug("白名单忽略无效 token: {}", e.getCode());
        }

        if (principal == null && !isPublic) {
            writeProblem(response, new AuthenticationException(
                    AuthenticationException.Code.MISSING_TOKEN, "缺少 Authorization Bearer token"));
            return;
        }

        if (principal != null) {
            UserContext.set(principal);
        }
        try {
            chain.doFilter(request, response);
        } finally {
            UserContext.clear();
        }
    }

    /** 解析 Authorization header；无 token 返回 null；有 token 但无效抛出。 */
    private UserPrincipal tryAuthenticate(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER)) {
            return null;
        }
        String token = header.substring(BEARER.length()).trim();
        if (token.isEmpty()) {
            return null;
        }
        String sessionId = jwtTokenProvider.parseSessionId(token);
        Optional<Session> session = sessionStore.findBySessionId(sessionId);
        if (session.isEmpty()) {
            throw new AuthenticationException(AuthenticationException.Code.NO_SESSION,
                    "会话已失效，请重新登录");
        }
        Session s = session.get();
        return new UserPrincipal(s.userId(), s.tenantId(), s.username(), s.sessionId(),
                s.tenantOwner(), s.mainDeptId(), false, null, s.realName());
    }

    private void writeProblem(HttpServletResponse response, AuthenticationException e) throws IOException {
        HttpStatus status = switch (e.getCode()) {
            case DISABLED -> HttpStatus.FORBIDDEN;
            default -> HttpStatus.UNAUTHORIZED;
        };
        Map<String, Object> body = new LinkedHashMap<>(6);
        body.put("type", "https://wen-saas-boot/errors/auth/" + e.getCode().name().toLowerCase());
        body.put("title", "Authentication failed");
        body.put("status", status.value());
        body.put("detail", e.getMessage());
        body.put("code", e.getCode().name());
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(JSON.toJSONString(body));
    }
}
