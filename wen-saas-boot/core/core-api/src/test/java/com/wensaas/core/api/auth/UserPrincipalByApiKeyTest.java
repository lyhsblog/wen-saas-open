package com.wensaas.core.api.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.wensaas.core.api.auth.user.UserPrincipal;

/**
 * UserPrincipal 的 byApiKey / apiKeyId 字段（开放 API 凭证登入）契约校验。
 */
class UserPrincipalByApiKeyTest {

    @Test
    void normalUserHasByApiKeyFalse() {
        UserPrincipal p = new UserPrincipal(10L, 1L, "alice", "sess-1",
                false, null, false, null);
        assertThat(p.byApiKey()).isFalse();
        assertThat(p.apiKeyId()).isNull();
    }

    @Test
    void byApiKeyTrueRequiresApiKeyId() {
        assertThatThrownBy(() -> new UserPrincipal(0L, 1L, "api", "sess",
                false, null, true, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("apiKeyId");
    }

    @Test
    void byApiKeyTrueWithApiKeyIdOk() {
        UserPrincipal p = new UserPrincipal(0L, 1L, "api-key:ak_xxx", "api-key:42",
                false, null, true, 42L);
        assertThat(p.byApiKey()).isTrue();
        assertThat(p.apiKeyId()).isEqualTo(42L);
    }
}
