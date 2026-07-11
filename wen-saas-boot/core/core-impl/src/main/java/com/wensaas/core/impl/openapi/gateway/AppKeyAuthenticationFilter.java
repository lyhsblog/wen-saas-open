package com.wensaas.core.impl.openapi.gateway;

import com.alibaba.fastjson2.JSON;
import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.auth.user.UserPrincipal;
import com.wensaas.core.api.idempotent.IdempotencyGuard;
import com.wensaas.core.api.openapi.OpenApiAuthenticationException;
import com.wensaas.core.api.tenant.TenantContext;
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
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import com.wensaas.core.impl.openapi.OpenApiProperties;
import com.wensaas.core.impl.openapi.calllog.ApiCallLogFilter;
import com.wensaas.core.impl.openapi.key.ApiKeyResolver;
import com.wensaas.core.impl.openapi.key.SysApiKey;
import com.wensaas.core.impl.openapi.ratelimit.RateLimiter;

/**
 * 开放 API AppKey 鉴权 Filter。
 *
 * <p>顺序：{@code HIGHEST_PRECEDENCE + 100}，晚于 {@code RequestIdFilter}（{@code HIGHEST_PRECEDENCE}）
 * 与 JWT {@code AuthenticationFilter}（{@code +50}）。
 *
 * <p>仅作用于 {@code wen-saas-boot.openapi.path-prefix}（默认 {@code /api/v1/openapi/**}）路径。
 * {@code permit-all} 子路径直接放行（用于健康探针等）。
 *
 * <p>鉴权失败响应统一 401 + Problem JSON；具体 error code 仅 log.warn 不暴露给客户端（防探测）。
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public class AppKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AppKeyAuthenticationFilter.class);

    public static final String HEADER_API_KEY = "X-Wen-API-Key";
    public static final String HEADER_SIGNATURE = "X-Wen-Signature";
    public static final String HEADER_TIMESTAMP = "X-Wen-Timestamp";
    public static final String HEADER_NONCE = "X-Wen-Nonce";
    public static final String HEADER_RATE_LIMIT = "X-RateLimit-Limit";
    public static final String HEADER_RATE_REMAINING = "X-RateLimit-Remaining";
    public static final String HEADER_RATE_RESET = "X-RateLimit-Reset";
    public static final String HEADER_RETRY_AFTER = "Retry-After";

    /** 鉴权成功后写入 request attribute，供外层 {@code ApiCallLogFilter} 在 finally
     *  中读取（此时 ThreadLocal UserContext/TenantContext 已被本 Filter clear）。 */
    public static final String ATTR_PRINCIPAL = "wenSaasBoot.openapi.principal";

    /** nonce 去重域，按凭证隔离（不同凭证的 nonce 互不影响）。 */
    private static final String NONCE_SCOPE = "openapi-nonce";

    private final ApiKeyResolver resolver;
    private final HmacSignatureVerifier verifier;
    private final IpWhitelistMatcher ipMatcher;
    private final RateLimiter rateLimiter;
    private final IdempotencyGuard idempotencyGuard;
    private final OpenApiProperties props;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public AppKeyAuthenticationFilter(ApiKeyResolver resolver,
                                       HmacSignatureVerifier verifier,
                                       IpWhitelistMatcher ipMatcher,
                                       RateLimiter rateLimiter,
                                       IdempotencyGuard idempotencyGuard,
                                       OpenApiProperties props) {
        this.resolver = resolver;
        this.verifier = verifier;
        this.ipMatcher = ipMatcher;
        this.rateLimiter = rateLimiter;
        this.idempotencyGuard = idempotencyGuard;
        this.props = props;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        String prefix = props.getPathPrefix();
        if (prefix == null || prefix.isBlank()) {
            return true;
        }
        // 仅 path-prefix 子路径走开放 API 鉴权
        return !path.startsWith(prefix);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        // 子路径白名单（如 ping）放行
        if (isPermitAll(path)) {
            chain.doFilter(request, response);
            return;
        }

        HttpServletRequest effective = request;
        try {
            // 1. 解析 X-Wen-API-Key
            String apiKey = request.getHeader(HEADER_API_KEY);
            SysApiKey keyMeta = resolver.resolve(apiKey);

            // 2. IP 白名单
            String clientIp = resolveClientIp(request);
            if (!ipMatcher.matches(clientIp, keyMeta.getIpWhitelist())) {
                throw new OpenApiAuthenticationException(
                        OpenApiAuthenticationException.Code.IP_REJECTED,
                        "客户端 IP 不在凭证白名单内");
            }

            // 3. HMAC 签名（require_signature=true 或客户端带签名时校验）
            String sigHeader = request.getHeader(HEADER_SIGNATURE);
            String tsHeader = request.getHeader(HEADER_TIMESTAMP);
            String nonceHeader = request.getHeader(HEADER_NONCE);
            boolean signingRequired = Boolean.TRUE.equals(keyMeta.getRequireSignature())
                    || (sigHeader != null && !sigHeader.isBlank());
            if (signingRequired) {
                if (sigHeader == null || sigHeader.isBlank() || tsHeader == null || tsHeader.isBlank()
                        || nonceHeader == null || nonceHeader.isBlank()) {
                    throw new OpenApiAuthenticationException(
                            OpenApiAuthenticationException.Code.MISSING_HEADER,
                            "缺少 X-Wen-Signature / X-Wen-Timestamp / X-Wen-Nonce");
                }
                String nonce = nonceHeader.trim();
                long timestamp;
                try {
                    timestamp = Long.parseLong(tsHeader.trim());
                } catch (NumberFormatException e) {
                    throw new OpenApiAuthenticationException(
                            OpenApiAuthenticationException.Code.EXPIRED_TIMESTAMP,
                            "X-Wen-Timestamp 非法");
                }
                long nowSec = Instant.now().getEpochSecond();
                long skew = Math.abs(nowSec - timestamp);
                if (skew > props.getTimestampSkew().toSeconds()) {
                    throw new OpenApiAuthenticationException(
                            OpenApiAuthenticationException.Code.EXPIRED_TIMESTAMP,
                            "X-Wen-Timestamp 偏差超过允许范围");
                }
                // 缓存 body 让 Controller 二次读取
                byte[] body = CachedBodyHttpServletRequest.readBody(request, (int) props.getMaxBodyBytes().toBytes());
                if (body == null) {
                    throw new OpenApiAuthenticationException(
                            OpenApiAuthenticationException.Code.PAYLOAD_TOO_LARGE,
                            "请求体超过限制");
                }
                effective = new CachedBodyHttpServletRequest(request, body);
                String bodyStr = new String(body, java.nio.charset.StandardCharsets.UTF_8);
                String plainSecret = resolver.decryptSecret(keyMeta);
                String expected = verifier.sign(plainSecret,
                        request.getMethod(), request.getRequestURI(), bodyStr, timestamp, nonce);
                if (!verifier.verify(expected, sigHeader.trim())) {
                    throw new OpenApiAuthenticationException(
                            OpenApiAuthenticationException.Code.INVALID_SIGNATURE,
                            "签名校验失败");
                }
                // nonce 一次性校验：签名通过后才声明，避免攻击者用伪造 nonce 抢占合法 nonce。
                // TTL 取 2×timestampSkew，覆盖「时间戳处于未来 skew 端」时的整段可重放窗口。
                Duration nonceTtl = props.getTimestampSkew().multipliedBy(2);
                String nonceScope = NONCE_SCOPE + ":" + keyMeta.getId();
                if (!idempotencyGuard.firstSeen(nonceScope, nonce, nonceTtl)) {
                    throw new OpenApiAuthenticationException(
                            OpenApiAuthenticationException.Code.REPLAYED_NONCE,
                            "X-Wen-Nonce 已被使用（重放）");
                }
            }

            // 4. 限流：租户级窗口 + 凭证级窗口两级叠加，任一超限即 429。
            //    凭证设了 rate_limit_qps 才走凭证级；两级各自 ZADD，被拒一级会给另一级留一个
            //    "幽灵"计数（限流器本就 fail-open 降级，此轻微不精确可接受）。
            RateLimiter.Decision decision = acquireRateLimit(keyMeta);
            response.setHeader(HEADER_RATE_LIMIT, String.valueOf(decision.limit()));
            response.setHeader(HEADER_RATE_REMAINING, String.valueOf(Math.max(0, decision.remaining())));
            response.setHeader(HEADER_RATE_RESET, String.valueOf(decision.resetEpochSecond()));
            if (!decision.allowed()) {
                long retryAfter = Math.max(1L, decision.resetEpochSecond() - Instant.now().getEpochSecond());
                response.setHeader(HEADER_RETRY_AFTER, String.valueOf(retryAfter));
                throw new OpenApiAuthenticationException(
                        OpenApiAuthenticationException.Code.RATE_LIMIT_EXCEEDED,
                        "请求频率超限");
            }

            // 5. 注入 UserPrincipal（byApiKey=true）+ TenantContext。
            //    功能权限按凭证创建者身份走 @RequirePermission；数据权限对 byApiKey 不做行级过滤
            //    （租户级系统对接定位，租户内全量数据）。
            UserPrincipal principal = new UserPrincipal(
                    keyMeta.getCreatorId() == null ? 0L : keyMeta.getCreatorId(),
                    keyMeta.getTenantId(),
                    "api-key:" + keyMeta.getApiKey(),
                    "api-key:" + keyMeta.getId(),
                    false, null, true, keyMeta.getId());
            UserContext.set(principal);
            TenantContext.set(keyMeta.getTenantId());
            // 写 request attribute；外层 ApiCallLogFilter 在 finally 读取（ThreadLocal 此时已 clear）
            request.setAttribute(ATTR_PRINCIPAL, principal);

            chain.doFilter(effective, response);
        } catch (OpenApiAuthenticationException e) {
            writeProblem(response, e);
        } finally {
            UserContext.clear();
            TenantContext.clear();
        }
    }

    /**
     * 两级限流：租户级窗口 + （凭证设了 rate_limit_qps 时）凭证级窗口。
     * 放行 = 两级都放行；响应头取较紧的一级（剩余配额更小者）。
     */
    private RateLimiter.Decision acquireRateLimit(SysApiKey keyMeta) {
        RateLimiter.Decision tenant = rateLimiter.tryAcquire(keyMeta.getTenantId(), null);
        Integer credQps = keyMeta.getRateLimitQps();
        if (credQps == null) {
            return tenant;
        }
        RateLimiter.Decision cred = rateLimiter.tryAcquire(keyMeta.getTenantId(), keyMeta.getId(), credQps);
        RateLimiter.Decision tighter = cred.remaining() <= tenant.remaining() ? cred : tenant;
        return new RateLimiter.Decision(
                tenant.allowed() && cred.allowed(),
                tighter.limit(), tighter.remaining(), tighter.resetEpochSecond());
    }

    private boolean isPermitAll(String path) {
        for (String pattern : props.getPermitAll()) {
            if (pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    private static String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma < 0 ? xff : xff.substring(0, comma)).trim();
        }
        return request.getRemoteAddr();
    }

    private void writeProblem(HttpServletResponse response, OpenApiAuthenticationException e) throws IOException {
        HttpStatus status = switch (e.getCode()) {
            case RATE_LIMIT_EXCEEDED -> HttpStatus.TOO_MANY_REQUESTS;
            case PAYLOAD_TOO_LARGE -> HttpStatus.PAYLOAD_TOO_LARGE;
            default -> HttpStatus.UNAUTHORIZED;
        };
        log.warn("openapi.auth.fail code={} status={} msg={}", e.getCode(), status.value(), e.getMessage());
        Map<String, Object> body = new LinkedHashMap<>(4);
        body.put("type", "https://wen-saas-boot/errors/openapi");
        body.put("title", status.getReasonPhrase());
        body.put("status", status.value());
        // 客户端可见的 detail 不含具体错误码（防探测）；运维查 log
        body.put("detail", switch (e.getCode()) {
            case RATE_LIMIT_EXCEEDED -> "Request rate limit exceeded";
            case PAYLOAD_TOO_LARGE -> "Request body too large";
            default -> "Authentication failed";
        });
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(JSON.toJSONString(body));
    }
}
