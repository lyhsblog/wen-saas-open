package com.wensaas.core.impl.auth.oauth;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.UUID;

/**
 * OAuth 登录 CSRF {@code state} 一次性存储（架构审查 M4）。
 *
 * <p>Key 形如 {@code wen-saas-boot:oauth-state:{state}}，value=绑定的 {@code provider}，TTL=10min。
 *
 * <p>流程：前端发起第三方登录前先调 {@code POST /api/auth/oauth/{provider}/state} 经 {@link #issue}
 * 取一次性 state，跳转渠道授权页时带上；渠道回调把 state 原样带回前端，前端再随 authCode POST /login，
 * 后端 {@link #consume} 比对消费。
 *
 * <p>消费用 {@link StringRedisTemplate.ValueOperations#getAndDelete}（GETDEL 原子操作）：
 * <ul>
 *   <li><b>防 CSRF</b>：攻击者无法伪造一个「服务端签发过且尚未消费」的 state；</li>
 *   <li><b>防重放</b>：消费即删，同一 state 第二次必失败；</li>
 *   <li><b>provider 绑定</b>：state 与签发时的渠道绑定，跨渠道串用也判失败。</li>
 * </ul>
 */
public class OAuthStateStore {

    static final String KEY_PREFIX = "wen-saas-boot:oauth-state:";
    static final Duration TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate redis;

    public OAuthStateStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** 签发随机 state 并绑定 provider（TTL=10min），返回 state 串。 */
    public String issue(String provider) {
        String state = UUID.randomUUID().toString().replace("-", "");
        redis.opsForValue().set(keyOf(state), provider, TTL);
        return state;
    }

    /**
     * 一次性消费：state 存在、未过期、未被消费，且绑定的 provider 一致才算通过。
     * 任一不满足（含 provider/state 空）返回 {@code false}。
     */
    public boolean consume(String provider, String state) {
        if (provider == null || provider.isBlank() || state == null || state.isBlank()) {
            return false;
        }
        String bound = redis.opsForValue().getAndDelete(keyOf(state));
        return bound != null && bound.equals(provider);
    }

    /** state TTL 秒数（供签发响应回传 expiresIn）。 */
    public long ttlSeconds() {
        return TTL.toSeconds();
    }

    static String keyOf(String state) {
        return KEY_PREFIX + state;
    }
}
