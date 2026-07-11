package com.wensaas.core.impl.auth;

import com.wensaas.core.api.auth.login.AuthenticationException;
import com.wensaas.core.impl.auth.session.JwtTokenProvider;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    private static AuthProperties propsWith(String secret, Duration ttl, String issuer) {
        AuthProperties p = new AuthProperties();
        p.setJwtSecret(secret);
        p.setTokenTtl(ttl);
        // JWT exp 现读 access-token-ttl（与会话/refresh TTL 解耦），测试令两者一致以复用原断言。
        p.setAccessTokenTtl(ttl);
        p.setIssuer(issuer);
        return p;
    }

    private static final String SECRET = "this-is-a-test-secret-with-32-bytes!!";

    @Test
    void issueAndParse_roundtrip() {
        JwtTokenProvider p = new JwtTokenProvider(propsWith(SECRET, Duration.ofMinutes(5), "wen"));
        String token = p.issue("session-abc");
        assertThat(p.parseSessionId(token)).isEqualTo("session-abc");
    }

    @Test
    void parse_expiredToken_throwsExpired() throws Exception {
        // 用极短 TTL（10ms），睡眠后解析
        JwtTokenProvider p = new JwtTokenProvider(propsWith(SECRET, Duration.ofMillis(10), "wen"));
        String token = p.issue("s");
        Thread.sleep(60);
        assertThatThrownBy(() -> p.parseSessionId(token))
                .isInstanceOf(AuthenticationException.class)
                .extracting(e -> ((AuthenticationException) e).getCode())
                .isEqualTo(AuthenticationException.Code.EXPIRED);
    }

    @Test
    void parse_tamperedToken_throwsInvalid() {
        JwtTokenProvider p = new JwtTokenProvider(propsWith(SECRET, Duration.ofMinutes(5), "wen"));
        String token = p.issue("s");
        String tampered = token.substring(0, token.length() - 2) + "xx";
        assertThatThrownBy(() -> p.parseSessionId(tampered))
                .isInstanceOf(AuthenticationException.class)
                .extracting(e -> ((AuthenticationException) e).getCode())
                .isEqualTo(AuthenticationException.Code.INVALID_TOKEN);
    }

    @Test
    void parse_wrongSecret_throwsInvalid() {
        JwtTokenProvider issuer = new JwtTokenProvider(propsWith(SECRET, Duration.ofMinutes(5), "wen"));
        JwtTokenProvider other = new JwtTokenProvider(
                propsWith("different-secret-with-32-bytes-padding!!", Duration.ofMinutes(5), "wen"));
        String token = issuer.issue("s");
        assertThatThrownBy(() -> other.parseSessionId(token))
                .isInstanceOf(AuthenticationException.class)
                .extracting(e -> ((AuthenticationException) e).getCode())
                .isEqualTo(AuthenticationException.Code.INVALID_TOKEN);
    }

    @Test
    void parse_garbageInput_throwsInvalid() {
        JwtTokenProvider p = new JwtTokenProvider(propsWith(SECRET, Duration.ofMinutes(5), "wen"));
        assertThatThrownBy(() -> p.parseSessionId("not-a-jwt"))
                .isInstanceOf(AuthenticationException.class)
                .extracting(e -> ((AuthenticationException) e).getCode())
                .isEqualTo(AuthenticationException.Code.INVALID_TOKEN);
    }

    @Test
    void parse_issuerMismatch_throwsInvalid() {
        JwtTokenProvider issuer = new JwtTokenProvider(propsWith(SECRET, Duration.ofMinutes(5), "other-issuer"));
        JwtTokenProvider verifier = new JwtTokenProvider(propsWith(SECRET, Duration.ofMinutes(5), "wen"));
        String token = issuer.issue("s");
        assertThatThrownBy(() -> verifier.parseSessionId(token))
                .isInstanceOf(AuthenticationException.class)
                .extracting(e -> ((AuthenticationException) e).getCode())
                .isEqualTo(AuthenticationException.Code.INVALID_TOKEN);
    }

    @Test
    void construct_shortSecret_throws() {
        AuthProperties p = propsWith("short", Duration.ofMinutes(5), "wen");
        assertThatThrownBy(() -> new JwtTokenProvider(p))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("256bit");
    }

    @Test
    void tokenTtl_reflectsProps() {
        JwtTokenProvider p = new JwtTokenProvider(propsWith(SECRET, Duration.ofMinutes(7), "wen"));
        assertThat(p.tokenTtl()).isEqualTo(Duration.ofMinutes(7));
    }
}
