package com.wensaas.core.impl.auth.session;

import com.wensaas.core.api.auth.user.AuthenticatedIdentity;
import com.wensaas.core.impl.auth.integration.AuthTestApp;
import com.wensaas.core.impl.auth.session.RedisSessionStore;
import com.wensaas.core.impl.auth.session.RefreshTokenService;
import com.wensaas.core.impl.auth.session.SessionStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RefreshTokenService 集成测试（连本机 Redis）：覆盖签发 / 轮换 / 重用检测 / 吊销。
 */
@SpringBootTest(classes = AuthTestApp.class)
@ActiveProfiles("dev")
class RefreshTokenServiceIntegrationTest {

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private SessionStore sessionStore;

    @Autowired
    private StringRedisTemplate redis;

    private String newSession() {
        return sessionStore.createSession(
                new AuthenticatedIdentity(900L, 0L, "rtUser", false, null, false, null));
    }

    @Test
    void issue_then_rotate_returnsNewToken_andExtendsSession() {
        String sid = newSession();
        String raw = refreshTokenService.issue(sid, false);

        RefreshTokenService.RotateResult r = refreshTokenService.rotate(raw);

        assertThat(r.status()).isEqualTo(RefreshTokenService.Status.OK);
        assertThat(r.sessionId()).isEqualTo(sid);
        assertThat(r.newRefreshToken()).isNotBlank().isNotEqualTo(raw);
        // 会话 TTL 被顺延（仍存活）
        Long ttl = redis.getExpire(RedisSessionStore.keyOf(sid), TimeUnit.SECONDS);
        assertThat(ttl).isNotNull().isGreaterThan(0);

        sessionStore.invalidate(sid);
        refreshTokenService.revoke(sid);
    }

    @Test
    void rotate_oldTokenAfterRotation_isReuse_andKillsSession() {
        String sid = newSession();
        String raw1 = refreshTokenService.issue(sid, false);
        RefreshTokenService.RotateResult first = refreshTokenService.rotate(raw1);
        assertThat(first.status()).isEqualTo(RefreshTokenService.Status.OK);

        // 重放已被轮换掉的旧 refresh → 判定被盗 → 踢整会话
        RefreshTokenService.RotateResult reuse = refreshTokenService.rotate(raw1);

        assertThat(reuse.status()).isEqualTo(RefreshTokenService.Status.REUSE);
        assertThat(sessionStore.findBySessionId(sid)).isEmpty();
        // 会话被踢后，连最新的 refresh 也失效（当前代次索引已删）
        assertThat(refreshTokenService.rotate(first.newRefreshToken()).status())
                .isEqualTo(RefreshTokenService.Status.INVALID);
    }

    @Test
    void rotate_unknownToken_isInvalid() {
        assertThat(refreshTokenService.rotate("nonexistent-token").status())
                .isEqualTo(RefreshTokenService.Status.INVALID);
        assertThat(refreshTokenService.rotate(null).status())
                .isEqualTo(RefreshTokenService.Status.INVALID);
    }

    @Test
    void revoke_thenRotate_isInvalid() {
        String sid = newSession();
        String raw = refreshTokenService.issue(sid, false);

        refreshTokenService.revoke(sid);

        assertThat(refreshTokenService.rotate(raw).status())
                .isEqualTo(RefreshTokenService.Status.INVALID);
        sessionStore.invalidate(sid);
    }

    @Test
    void rememberMe_usesLongerTtl() {
        String sid = newSession();
        refreshTokenService.issue(sid, true);

        Long ttl = redis.getExpire(RedisSessionStore.keyOf(sid), TimeUnit.SECONDS);
        // remember = 30d，远大于默认会话时长
        assertThat(ttl).isNotNull().isGreaterThan(TimeUnit.DAYS.toSeconds(7));

        sessionStore.invalidate(sid);
        refreshTokenService.revoke(sid);
    }
}
