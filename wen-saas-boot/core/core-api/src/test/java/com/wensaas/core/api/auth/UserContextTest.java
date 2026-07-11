package com.wensaas.core.api.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.auth.user.UserPrincipal;

class UserContextTest {

    private static final UserPrincipal A = new UserPrincipal(1L, 10L, "a", "sa", false, null, false, null);
    private static final UserPrincipal B = new UserPrincipal(2L, 20L, "b", "sb", false, null, false, null);

    @AfterEach
    void cleanup() {
        UserContext.clear();
    }

    @Test
    void setAndGet_roundtrip() {
        UserContext.set(A);
        assertThat(UserContext.get()).isEqualTo(A);
    }

    @Test
    void get_unset_returnsNull() {
        assertThat(UserContext.get()).isNull();
    }

    @Test
    void require_unset_throws() {
        assertThatThrownBy(UserContext::require)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("UserContext 未设置");
    }

    @Test
    void clear_removesValue() {
        UserContext.set(A);
        UserContext.clear();
        assertThat(UserContext.get()).isNull();
    }

    @Test
    void runAs_restoresPreviousAfterExit() {
        UserContext.set(A);
        UserContext.runAs(B, () -> assertThat(UserContext.get()).isEqualTo(B));
        assertThat(UserContext.get()).isEqualTo(A);
    }

    @Test
    void runAs_fromUnset_clearsAfterExit() {
        assertThat(UserContext.get()).isNull();
        UserContext.runAs(A, () -> assertThat(UserContext.get()).isEqualTo(A));
        assertThat(UserContext.get()).isNull();
    }

    @Test
    void runAs_restoresOnException() {
        UserContext.set(A);
        try {
            UserContext.runAs(B, () -> {
                throw new RuntimeException("boom");
            });
        } catch (RuntimeException ignored) {
        }
        assertThat(UserContext.get()).isEqualTo(A);
    }

    @Test
    void callAs_returnsValueAndRestores() {
        UserContext.set(A);
        long got = UserContext.callAs(B, () -> UserContext.require().userId());
        assertThat(got).isEqualTo(2L);
        assertThat(UserContext.get()).isEqualTo(A);
    }

    @Test
    void threadLocal_doesNotLeakToChildThread() throws Exception {
        UserContext.set(A);
        AtomicReference<UserPrincipal> seen = new AtomicReference<>(A); // seed non-null
        Thread t = new Thread(() -> seen.set(UserContext.get()));
        t.start();
        t.join();
        assertThat(seen.get()).isNull();
    }
}
