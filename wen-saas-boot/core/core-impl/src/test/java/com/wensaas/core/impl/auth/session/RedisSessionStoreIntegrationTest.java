package com.wensaas.core.impl.auth.session;

import com.wensaas.core.api.auth.user.AuthenticatedIdentity;
import com.wensaas.core.impl.auth.integration.AuthTestApp;
import com.wensaas.core.impl.auth.password.SecurityPolicySettings;
import com.wensaas.core.impl.auth.session.RedisSessionStore;
import com.wensaas.core.impl.auth.session.Session;
import com.wensaas.core.impl.auth.session.SessionStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = AuthTestApp.class)
@ActiveProfiles("dev")
class RedisSessionStoreIntegrationTest {

    @Autowired
    private SessionStore sessionStore;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private SecurityPolicySettings securityPolicy;

    @Test
    void create_then_find_returnsSession() {
        AuthenticatedIdentity id = new AuthenticatedIdentity(100L, 0L, "admin", false, null, false, null);
        String sid = sessionStore.createSession(id);

        Optional<Session> got = sessionStore.findBySessionId(sid);

        assertThat(got).isPresent();
        Session s = got.get();
        assertThat(s.sessionId()).isEqualTo(sid);
        assertThat(s.userId()).isEqualTo(100L);
        assertThat(s.tenantId()).isEqualTo(0L);
        assertThat(s.username()).isEqualTo("admin");
        assertThat(s.loginAt()).isNotNull();
    }

    @Test
    void invalidate_makesSessionGone() {
        AuthenticatedIdentity id = new AuthenticatedIdentity(101L, 0L, "u", false, null, false, null);
        String sid = sessionStore.createSession(id);
        assertThat(sessionStore.findBySessionId(sid)).isPresent();

        sessionStore.invalidate(sid);

        assertThat(sessionStore.findBySessionId(sid)).isEmpty();
    }

    @Test
    void findBySessionId_unknown_returnsEmpty() {
        assertThat(sessionStore.findBySessionId("does-not-exist")).isEmpty();
        assertThat(sessionStore.findBySessionId("")).isEmpty();
        assertThat(sessionStore.findBySessionId(null)).isEmpty();
    }

    @Test
    void create_setsTtlOnRedisKey() {
        AuthenticatedIdentity id = new AuthenticatedIdentity(102L, 0L, "u", false, null, false, null);
        String sid = sessionStore.createSession(id);

        Long ttl = redis.getExpire(RedisSessionStore.keyOf(sid), TimeUnit.SECONDS);
        assertThat(ttl).isNotNull();
        // 会话 TTL 读 sys_setting(security_policy.session_ttl_seconds)，
        // 由 SecurityPolicySettings 统一解析（缺省回退 AuthProperties.tokenTtl）。
        long configured = securityPolicy.sessionTtlSeconds();
        // 允许一定误差（创建到读 expire 之间已经过去几毫秒）
        assertThat(ttl).isBetween(configured - 5, configured + 1);

        sessionStore.invalidate(sid);
    }

    @Test
    void createImpersonatedSession_storesOriginalSessionId() {
        String origSid = sessionStore.createSession(
                new AuthenticatedIdentity(1001L, 0L, "opsAdmin", false, null, false, null));
        AuthenticatedIdentity targetId = new AuthenticatedIdentity(200L, 100L, "member", false, null, false, null);

        String impSid = sessionStore.createImpersonatedSession(targetId, origSid, "opsAdmin");

        Optional<Session> got = sessionStore.findBySessionId(impSid);
        assertThat(got).isPresent();
        Session s = got.get();
        assertThat(s.originalSessionId()).isEqualTo(origSid);
        assertThat(s.operatorName()).isEqualTo("opsAdmin");
        assertThat(s.userId()).isEqualTo(200L);
        assertThat(s.tenantId()).isEqualTo(100L);

        sessionStore.invalidate(impSid);
        sessionStore.invalidate(origSid);
    }

    @Test
    void serialize_deserialize_preservesOriginalSessionId() {
        Session s = new Session("sid-x", 200L, 100L, "member", false, 5L,
                Instant.parse("2026-05-27T00:00:00Z"), Instant.parse("2026-05-27T01:00:00Z"),
                "orig-sid-abc", "opsAdmin", "张三");
        String json = RedisSessionStore.serialize(s);
        assertThat(json).contains("originalSessionId");
        assertThat(json).contains("operatorName");
        assertThat(json).contains("realName");

        Session roundTripped = RedisSessionStore.deserialize(json);
        assertThat(roundTripped.originalSessionId()).isEqualTo("orig-sid-abc");
        assertThat(roundTripped.operatorName()).isEqualTo("opsAdmin");
        assertThat(roundTripped.realName()).isEqualTo("张三");
        assertThat(roundTripped.userId()).isEqualTo(200L);
    }

    @Test
    void deserialize_oldJsonWithoutOriginalSessionId_returnsNull() {
        // 模拟旧格式 session JSON（无 originalSessionId / realName 字段）
        String oldJson = "{\"sessionId\":\"old-sid\",\"userId\":100,\"tenantId\":0,"
                + "\"username\":\"admin\",\"tenantOwner\":false,"
                + "\"loginAt\":\"2026-01-01T00:00:00Z\","
                + "\"lastAccessAt\":\"2026-01-01T00:00:00Z\"}";

        Session s = RedisSessionStore.deserialize(oldJson);
        assertThat(s.originalSessionId()).isNull();
        assertThat(s.operatorName()).isNull();
        assertThat(s.realName()).isNull();
        assertThat(s.userId()).isEqualTo(100L);
    }

    @Test
    void findByUser_returnsAllActiveSessions_sortedByLoginAtDesc() {
        long userId = 30001L;
        long tenantId = 0L;
        String s1 = sessionStore.createSession(new AuthenticatedIdentity(userId, tenantId, "u", false, null, false, null));
        String s2 = sessionStore.createSession(new AuthenticatedIdentity(userId, tenantId, "u", false, null, false, null));

        List<Session> sessions = sessionStore.findByUser(tenantId, userId);

        assertThat(sessions).extracting(Session::sessionId).containsExactlyInAnyOrder(s1, s2);

        sessionStore.invalidateAllByUser(tenantId, userId);
    }

    @Test
    void invalidateAllByUserExcept_keepsOnlyCurrent() {
        long userId = 30002L;
        long tenantId = 0L;
        String keep = sessionStore.createSession(new AuthenticatedIdentity(userId, tenantId, "u", false, null, false, null));
        String other = sessionStore.createSession(new AuthenticatedIdentity(userId, tenantId, "u", false, null, false, null));

        sessionStore.invalidateAllByUserExcept(tenantId, userId, keep);

        assertThat(sessionStore.findBySessionId(keep)).isPresent();
        assertThat(sessionStore.findBySessionId(other)).isEmpty();
        assertThat(sessionStore.findByUser(tenantId, userId)).extracting(Session::sessionId).containsExactly(keep);

        sessionStore.invalidate(keep);
    }

    @Test
    void invalidateAllByUser_clearsEverything() {
        long userId = 30003L;
        long tenantId = 0L;
        sessionStore.createSession(new AuthenticatedIdentity(userId, tenantId, "u", false, null, false, null));
        sessionStore.createSession(new AuthenticatedIdentity(userId, tenantId, "u", false, null, false, null));

        sessionStore.invalidateAllByUser(tenantId, userId);

        assertThat(sessionStore.findByUser(tenantId, userId)).isEmpty();
    }

    @Test
    void invalidate_removesFromUserIndex() {
        long userId = 30004L;
        long tenantId = 0L;
        String sid = sessionStore.createSession(new AuthenticatedIdentity(userId, tenantId, "u", false, null, false, null));
        assertThat(sessionStore.findByUser(tenantId, userId)).hasSize(1);

        sessionStore.invalidate(sid);

        assertThat(sessionStore.findByUser(tenantId, userId)).isEmpty();
    }

    @Test
    void createImpersonatedSession_blankOriginalSid_throws() {
        AuthenticatedIdentity id = new AuthenticatedIdentity(200L, 100L, "member", false, null, false, null);
        assertThatThrownBy(() -> sessionStore.createImpersonatedSession(id, null, "opsAdmin"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> sessionStore.createImpersonatedSession(id, "  ", "opsAdmin"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
