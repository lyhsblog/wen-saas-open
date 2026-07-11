package com.wensaas.core.impl.auth.session;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.wensaas.core.api.auth.user.AuthenticatedIdentity;
import com.wensaas.core.impl.auth.AuthProperties;
import com.wensaas.core.impl.auth.password.SecurityPolicySettings;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Redis 会话存储。Key 形如 {@code wen-saas-boot:session:{sessionId}}，Value 为 JSON。
 *
 * <p>序列化使用 fastjson2，{@link Instant} 以 ISO-8601 字符串保存，便于 redis-cli 直观查看。
 *
 * <p>另维护按用户的二级索引 Set {@code wen-saas-boot:user-sessions:{tenantId}:{userId}}，
 * 成员为该用户当前全部 sessionId。session 创建时入集 + 刷新集 TTL；失效时出集。
 * 支撑"列我的会话 / 下线其他 / 改密强制全下线"等运营/租户个人中心能力。
 */
public class RedisSessionStore implements SessionStore {

    static final String KEY_PREFIX = "wen-saas-boot:session:";
    static final String USER_INDEX_PREFIX = "wen-saas-boot:user-sessions:";

    private final StringRedisTemplate redis;
    private final Duration fallbackTtl;
    /** 会话 TTL 与 JWT exp 同源读 sys_setting(security_policy.session_ttl_seconds)；null 时退回 props。 */
    private final SecurityPolicySettings security;

    public RedisSessionStore(StringRedisTemplate redis, AuthProperties props) {
        this(redis, props, null);
    }

    public RedisSessionStore(StringRedisTemplate redis, AuthProperties props, SecurityPolicySettings security) {
        this.redis = redis;
        this.fallbackTtl = props.getTokenTtl();
        this.security = security;
    }

    /** 当前生效会话 TTL：与 {@link JwtTokenProvider#tokenTtl()} 同源，保证 token exp 与 session key 过期一致。 */
    private Duration ttl() {
        return security != null ? Duration.ofSeconds(security.sessionTtlSeconds()) : fallbackTtl;
    }

    @Override
    public String createSession(AuthenticatedIdentity identity) {
        return doCreate(identity, null, null);
    }

    @Override
    public String createImpersonatedSession(AuthenticatedIdentity identity, String originalSessionId, String operatorName) {
        if (originalSessionId == null || originalSessionId.isBlank()) {
            throw new IllegalArgumentException("originalSessionId 不能为空");
        }
        return doCreate(identity, originalSessionId, operatorName);
    }

    private String doCreate(AuthenticatedIdentity identity, String originalSessionId, String operatorName) {
        String sessionId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Session session = new Session(sessionId, identity.userId(), identity.tenantId(),
                identity.username(), identity.tenantOwner(), identity.mainDeptId(), now, now,
                originalSessionId, operatorName, identity.realName(), currentClientIp(), currentUserAgent());
        Duration ttl = ttl();
        redis.opsForValue().set(keyOf(sessionId), serialize(session), ttl);
        String userKey = userIndexKey(identity.tenantId(), identity.userId());
        redis.opsForSet().add(userKey, sessionId);
        // 刷新索引集 TTL = 会话 TTL；过期成员在 findByUser 时按需剔除。
        redis.expire(userKey, ttl);
        return sessionId;
    }

    @Override
    public void extendTtl(String sessionId, Duration ttl) {
        if (sessionId == null || sessionId.isBlank() || ttl == null) {
            return;
        }
        Optional<Session> existing = findBySessionId(sessionId);
        if (existing.isEmpty()) {
            return;
        }
        Session s = existing.get();
        Session touched = new Session(s.sessionId(), s.userId(), s.tenantId(), s.username(),
                s.tenantOwner(), s.mainDeptId(), s.loginAt(), Instant.now(),
                s.originalSessionId(), s.operatorName(), s.realName(), s.clientIp(), s.userAgent());
        redis.opsForValue().set(keyOf(sessionId), serialize(touched), ttl);
        redis.expire(userIndexKey(s.tenantId(), s.userId()), ttl);
    }

    @Override
    public Optional<Session> findBySessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Optional.empty();
        }
        String raw = redis.opsForValue().get(keyOf(sessionId));
        if (raw == null) {
            return Optional.empty();
        }
        return Optional.of(deserialize(raw));
    }

    @Override
    public void invalidate(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        // 先读出会话以定位其所属用户索引集，再删除；会话已不存在时仅 no-op。
        Optional<Session> existing = findBySessionId(sessionId);
        existing.ifPresent(s -> redis.opsForSet().remove(userIndexKey(s.tenantId(), s.userId()), sessionId));
        redis.delete(keyOf(sessionId));
    }

    @Override
    public List<Session> findByUser(long tenantId, long userId) {
        String userKey = userIndexKey(tenantId, userId);
        Set<String> members = redis.opsForSet().members(userKey);
        if (members == null || members.isEmpty()) {
            return List.of();
        }
        List<Session> result = new ArrayList<>(members.size());
        for (String sessionId : members) {
            Optional<Session> s = findBySessionId(sessionId);
            if (s.isPresent()) {
                result.add(s.get());
            } else {
                // 会话 key 已过期 → 剔除悬挂索引成员。
                redis.opsForSet().remove(userKey, sessionId);
            }
        }
        result.sort(Comparator.comparing(Session::loginAt).reversed());
        return result;
    }

    @Override
    public void invalidateAllByUser(long tenantId, long userId) {
        invalidateAllByUserExcept(tenantId, userId, null);
    }

    @Override
    public void invalidateAllByUserExcept(long tenantId, long userId, String keepSessionId) {
        String userKey = userIndexKey(tenantId, userId);
        Set<String> members = redis.opsForSet().members(userKey);
        if (members == null || members.isEmpty()) {
            return;
        }
        for (String sessionId : members) {
            if (sessionId.equals(keepSessionId)) {
                continue;
            }
            redis.delete(keyOf(sessionId));
            redis.opsForSet().remove(userKey, sessionId);
        }
    }

    /** 当前请求客户端 IP：优先 X-Forwarded-For 首段（反代场景），否则 remoteAddr。无请求上下文返回 null。 */
    private static String currentClientIp() {
        HttpServletRequest req = currentRequest();
        if (req == null) {
            return null;
        }
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }

    /** 当前请求 User-Agent 原文（截断 ≤512 防 Redis 膨胀）。无请求上下文 / 缺头返回 null。 */
    private static String currentUserAgent() {
        HttpServletRequest req = currentRequest();
        if (req == null) {
            return null;
        }
        String ua = req.getHeader("User-Agent");
        if (ua == null || ua.isBlank()) {
            return null;
        }
        return ua.length() > 512 ? ua.substring(0, 512) : ua;
    }

    private static HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest();
        }
        return null;
    }

    static String keyOf(String sessionId) {
        return KEY_PREFIX + sessionId;
    }

    static String userIndexKey(long tenantId, long userId) {
        return USER_INDEX_PREFIX + tenantId + ":" + userId;
    }

    static String serialize(Session s) {
        JSONObject jo = new JSONObject(10);
        jo.put("sessionId", s.sessionId());
        jo.put("userId", s.userId());
        jo.put("tenantId", s.tenantId());
        jo.put("username", s.username());
        jo.put("tenantOwner", s.tenantOwner());
        jo.put("mainDeptId", s.mainDeptId());
        jo.put("loginAt", s.loginAt().toString());
        jo.put("lastAccessAt", s.lastAccessAt().toString());
        if (s.originalSessionId() != null) {
            jo.put("originalSessionId", s.originalSessionId());
        }
        if (s.operatorName() != null) {
            jo.put("operatorName", s.operatorName());
        }
        if (s.realName() != null) {
            jo.put("realName", s.realName());
        }
        if (s.clientIp() != null) {
            jo.put("clientIp", s.clientIp());
        }
        if (s.userAgent() != null) {
            jo.put("userAgent", s.userAgent());
        }
        return jo.toJSONString();
    }

    static Session deserialize(String json) {
        try {
            JSONObject jo = JSON.parseObject(json);
            // 老 session 可能不含 mainDeptId 字段；getLongValue 缺字段会返回 0 被误判为部门 ID=0。
            Long mainDeptId = (jo.containsKey("mainDeptId") && jo.get("mainDeptId") != null)
                    ? jo.getLong("mainDeptId")
                    : null;
            // 老 session 不含 originalSessionId；缺字段时为 null（普通登录）。
            String originalSessionId = (jo.containsKey("originalSessionId") && jo.get("originalSessionId") != null)
                    ? jo.getString("originalSessionId")
                    : null;
            // 老 session / 普通登录不含 operatorName；缺字段时为 null。
            String operatorName = (jo.containsKey("operatorName") && jo.get("operatorName") != null)
                    ? jo.getString("operatorName")
                    : null;
            // 老 session 不含 realName；缺字段时为 null（消费侧回退账号 / 按需查库）。
            String realName = (jo.containsKey("realName") && jo.get("realName") != null)
                    ? jo.getString("realName")
                    : null;
            // 老 session 不含 clientIp / userAgent；缺字段时为 null（前端展示「未知设备」）。
            String clientIp = (jo.containsKey("clientIp") && jo.get("clientIp") != null)
                    ? jo.getString("clientIp")
                    : null;
            String userAgent = (jo.containsKey("userAgent") && jo.get("userAgent") != null)
                    ? jo.getString("userAgent")
                    : null;
            return new Session(
                    jo.getString("sessionId"),
                    jo.getLongValue("userId"),
                    jo.getLongValue("tenantId"),
                    jo.getString("username"),
                    jo.getBooleanValue("tenantOwner"),
                    mainDeptId,
                    Instant.parse(jo.getString("loginAt")),
                    Instant.parse(jo.getString("lastAccessAt")),
                    originalSessionId,
                    operatorName,
                    realName,
                    clientIp,
                    userAgent
            );
        } catch (RuntimeException e) {
            throw new IllegalStateException("Session JSON 反序列化失败：" + json, e);
        }
    }
}
