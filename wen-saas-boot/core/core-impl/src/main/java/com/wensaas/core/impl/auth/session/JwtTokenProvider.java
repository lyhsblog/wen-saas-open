package com.wensaas.core.impl.auth.session;

import com.wensaas.core.api.auth.login.AuthenticationException;
import com.wensaas.core.impl.auth.AuthProperties;
import com.wensaas.core.impl.auth.password.SecurityPolicySettings;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * JWT 签发与解析。HS256，秘钥与 issuer 由 {@link AuthProperties} 注入。
 *
 * <p>claim 约定：{@code sub = sessionId}、{@code iat}、{@code exp}、{@code iss}。
 */
public class JwtTokenProvider {

    private final SecretKey key;
    private final String issuer;
    /**
     * Access token（JWT）TTL，短时效（默认 15min）。
     *
     * <p>注意：自 refresh token 改造后，JWT exp 与 Redis 会话 / refresh TTL 已解耦——
     * JWT 只活 access-token-ttl，会话靠 {@code /api/auth/refresh} 滑动续期（见 {@link RefreshTokenService}）。
     * 运营在线配置 {@code session_ttl_seconds} 现仅控制会话 / refresh TTL（{@link RedisSessionStore}），不再影响 JWT exp。
     */
    private final Duration accessTtl;

    public JwtTokenProvider(AuthProperties props) {
        this(props, null);
    }

    public JwtTokenProvider(AuthProperties props, SecurityPolicySettings security) {
        String secret = props.getJwtSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "wen-saas-boot.auth.jwt-secret 缺失或不足 256bit（32 字节）。"
                            + "请通过环境变量 WEN_SAAS_BOOT_JWT_SECRET 提供生产秘钥。");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = props.getIssuer();
        this.accessTtl = props.getAccessTokenTtl();
    }

    /** Access token 当前生效 TTL。 */
    private Duration currentTtl() {
        return accessTtl;
    }

    /**
     * 签发 token。
     *
     * @param sessionId Redis Session 标识，写入 {@code sub}
     */
    public String issue(String sessionId) {
        Instant now = Instant.now();
        Instant exp = now.plus(currentTtl());
        return Jwts.builder()
                .issuer(issuer)
                .subject(sessionId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * 解析 token，返回 {@code sub}（即 sessionId）。
     *
     * @throws AuthenticationException 验签失败 / 过期 / issuer 不匹配
     */
    public String parseSessionId(String token) {
        try {
            Jws<Claims> jws = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token);
            String sub = jws.getPayload().getSubject();
            if (sub == null || sub.isBlank()) {
                throw new AuthenticationException(AuthenticationException.Code.INVALID_TOKEN,
                        "JWT subject 为空");
            }
            return sub;
        } catch (ExpiredJwtException e) {
            throw new AuthenticationException(AuthenticationException.Code.EXPIRED, "JWT 已过期", e);
        } catch (JwtException | IllegalArgumentException e) {
            throw new AuthenticationException(AuthenticationException.Code.INVALID_TOKEN,
                    "JWT 无效：" + e.getMessage(), e);
        }
    }

    /** 当前 TTL，仅供登录响应回传 {@code expiresIn}。 */
    public Duration tokenTtl() {
        return currentTtl();
    }
}
