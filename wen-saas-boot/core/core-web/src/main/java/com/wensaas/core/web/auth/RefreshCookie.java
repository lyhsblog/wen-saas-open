package com.wensaas.core.web.auth;

import com.wensaas.core.impl.auth.AuthProperties;
import com.wensaas.core.impl.auth.session.RefreshTokenService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Refresh token 的 httpOnly cookie 读写门面。
 *
 * <p>cookie 属性：{@code httpOnly + Secure(可配) + SameSite=Strict + Path=/api/auth}。
 * 三端各为独立子域（{@code ops.} / {@code {租户}.} / {@code {租户}.m.}），cookie 不设 {@code Domain}、
 * 按 host-only 落到当前子域，天然隔离——运营与租户、租户之间互不覆盖。仅前端 XHR（同源）与
 * {@code /api/auth/refresh|logout} 用到；SameSite=Strict + 同源部署下 CSRF 被阻断，且 refresh 不落 JS 可读存储、抗 XSS 窃取。
 */
@Component
public class RefreshCookie {

    /** cookie 名。 */
    public static final String NAME = "wen_rt";
    private static final String PATH = "/api/auth";

    private final RefreshTokenService refreshTokenService;
    private final boolean secure;

    public RefreshCookie(RefreshTokenService refreshTokenService, AuthProperties props) {
        this.refreshTokenService = refreshTokenService;
        this.secure = props.isRefreshCookieSecure();
    }

    /**
     * 登录 / MFA / OAuth / 代登录兑换完成态调用：为会话签发首个 refresh 并种到响应 cookie。
     */
    public void issue(HttpServletResponse resp, String sessionId, boolean remember) {
        String refresh = refreshTokenService.issue(sessionId, remember);
        set(resp, refresh, refreshTokenService.ttl(remember));
    }

    /** /refresh 轮换后回种新 refresh cookie。 */
    public void rotate(HttpServletResponse resp, String newRefresh, boolean remember) {
        set(resp, newRefresh, refreshTokenService.ttl(remember));
    }

    /** 读取请求里的 refresh token；无则 null。 */
    public String read(HttpServletRequest req) {
        if (req.getCookies() == null) {
            return null;
        }
        for (Cookie c : req.getCookies()) {
            if (NAME.equals(c.getName())) {
                String v = c.getValue();
                return (v == null || v.isBlank()) ? null : v;
            }
        }
        return null;
    }

    /** 登出 / refresh 失效时清除 cookie。 */
    public void clear(HttpServletResponse resp) {
        set(resp, "", Duration.ZERO);
    }

    private void set(HttpServletResponse resp, String value, Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(PATH)
                .maxAge(maxAge)
                .build();
        resp.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
