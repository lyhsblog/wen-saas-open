package com.wensaas.core.impl.auth.session;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.wensaas.core.impl.auth.AuthProperties;
import com.wensaas.core.impl.auth.password.SecurityPolicySettings;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Refresh token 签发 / 轮换 / 重用检测。
 *
 * <p>令牌模型：短时效 access JWT（{@link JwtTokenProvider}）+ 长时效不透明 refresh。refresh **明文从不落库**，
 * 仅存 SHA-256 hash。refresh 经 httpOnly cookie（{@code RefreshCookie}）下发，access 过期时 {@code POST /api/auth/refresh}
 * 由浏览器自动携带 cookie 续期。
 *
 * <p>Redis 结构（均按 refresh TTL 存活、刷新滑动顺延）：
 * <ul>
 *   <li>{@code wen-saas-boot:refresh:{hash}} → {sessionId, gen, remember}：按 token 反查归属会话与代次。</li>
 *   <li>{@code wen-saas-boot:refresh-cur:{sessionId}} → {hash, gen, remember}：该会话当前有效 refresh 代次。</li>
 * </ul>
 *
 * <p>轮换 + 重用检测：每次 {@code rotate} 校验呈递串代次 == 会话当前代次 → 发新串、代次 +1、新串经 Set-Cookie 回种浏览器；
 * 若呈递的是已被轮换掉的旧代次（gen &lt; 当前）→ 判定 refresh 被盗重放 → {@link SessionStore#invalidate} 整个会话 → REUSE。
 */
public class RefreshTokenService {

    static final String TOKEN_PREFIX = "wen-saas-boot:refresh:";
    static final String CURRENT_PREFIX = "wen-saas-boot:refresh-cur:";

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();

    private final StringRedisTemplate redis;
    private final AuthProperties props;
    private final SecurityPolicySettings security;
    private final SessionStore sessionStore;

    public RefreshTokenService(StringRedisTemplate redis, AuthProperties props,
                               SecurityPolicySettings security, SessionStore sessionStore) {
        this.redis = redis;
        this.props = props;
        this.security = security;
        this.sessionStore = sessionStore;
    }

    /** 轮换结果状态。 */
    public enum Status { OK, INVALID, REUSE }

    /** 轮换结果。{@code OK} 时携带 sessionId + 新 refresh 明文（经 Set-Cookie 回种）+ remember（决定 TTL 档位）。 */
    public record RotateResult(Status status, String sessionId, String newRefreshToken, boolean remember) {
        static RotateResult ok(String sessionId, String token, boolean remember) {
            return new RotateResult(Status.OK, sessionId, token, remember);
        }
        static RotateResult invalid() {
            return new RotateResult(Status.INVALID, null, null, false);
        }
        static RotateResult reuse(String sessionId) {
            return new RotateResult(Status.REUSE, sessionId, null, false);
        }
    }

    /** 当前生效 refresh / 会话 TTL：勾"记住设备"用 30d，否则用运营可调的 session_ttl_seconds。 */
    public Duration ttl(boolean remember) {
        return remember ? props.getRememberRefreshTtl()
                : Duration.ofSeconds(security.sessionTtlSeconds());
    }

    /** 为会话签发首个 refresh（登录 / MFA 完成 / OAuth 完成时），返回明文。同时把会话 TTL 顺延到 refresh TTL。 */
    public String issue(String sessionId, boolean remember) {
        String raw = randomToken();
        String hash = sha256(raw);
        Duration ttl = ttl(remember);
        writeGeneration(sessionId, hash, 1, remember, ttl);
        sessionStore.extendTtl(sessionId, ttl);
        return raw;
    }

    /** 校验并轮换 refresh。成功返回新明文；过期/未知→INVALID；旧代次重放→REUSE（已踢会话）。 */
    public RotateResult rotate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return RotateResult.invalid();
        }
        String hash = sha256(rawToken);
        String tokenJson = redis.opsForValue().get(TOKEN_PREFIX + hash);
        if (tokenJson == null) {
            return RotateResult.invalid();
        }
        JSONObject tv = JSON.parseObject(tokenJson);
        String sessionId = tv.getString("sessionId");
        long gen = tv.getLongValue("gen");
        boolean remember = tv.getBooleanValue("remember");

        String curJson = redis.opsForValue().get(CURRENT_PREFIX + sessionId);
        if (curJson == null) {
            // 会话当前代次已不存在（会话过期/已登出）→ 视为无效。
            return RotateResult.invalid();
        }
        long curGen = JSON.parseObject(curJson).getLongValue("gen");
        if (gen < curGen) {
            // 呈递的是已被轮换掉的旧 refresh → 重放/被盗 → 踢整会话。
            sessionStore.invalidate(sessionId);
            redis.delete(CURRENT_PREFIX + sessionId);
            return RotateResult.reuse(sessionId);
        }

        // 正常轮换：代次 +1，发新串，顺延会话 TTL。旧 refresh:{hash} 不立即删，留作重放检测（其 gen 已小于当前）。
        long newGen = curGen + 1;
        String newRaw = randomToken();
        String newHash = sha256(newRaw);
        Duration ttl = ttl(remember);
        writeGeneration(sessionId, newHash, newGen, remember, ttl);
        sessionStore.extendTtl(sessionId, ttl);
        return RotateResult.ok(sessionId, newRaw, remember);
    }

    /** 登出时吊销会话的 refresh（删当前代次索引）。旧 token:{hash} 留 TTL 自然过期。 */
    public void revoke(String sessionId) {
        if (sessionId != null && !sessionId.isBlank()) {
            redis.delete(CURRENT_PREFIX + sessionId);
        }
    }

    private void writeGeneration(String sessionId, String hash, long gen, boolean remember, Duration ttl) {
        JSONObject token = new JSONObject(4);
        token.put("sessionId", sessionId);
        token.put("gen", gen);
        token.put("remember", remember);
        redis.opsForValue().set(TOKEN_PREFIX + hash, token.toJSONString(), ttl);

        JSONObject cur = new JSONObject(4);
        cur.put("hash", hash);
        cur.put("gen", gen);
        cur.put("remember", remember);
        redis.opsForValue().set(CURRENT_PREFIX + sessionId, cur.toJSONString(), ttl);
    }

    private static String randomToken() {
        byte[] buf = new byte[32];
        RANDOM.nextBytes(buf);
        return B64.encodeToString(buf);
    }

    private static String sha256(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
