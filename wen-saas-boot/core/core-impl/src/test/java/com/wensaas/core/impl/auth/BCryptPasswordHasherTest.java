package com.wensaas.core.impl.auth;

import com.wensaas.core.impl.auth.password.BCryptPasswordHasher;
import com.wensaas.core.impl.auth.password.PasswordHasher;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BCryptPasswordHasherTest {

    private final PasswordHasher hasher = new BCryptPasswordHasher();

    @Test
    void hashAndVerify_match() {
        String hash = hasher.hash("admin123");
        assertThat(hash).startsWith("$2"); // BCrypt 标识
        assertThat(hasher.verify("admin123", hash)).isTrue();
    }

    @Test
    void verify_wrongPassword_false() {
        String hash = hasher.hash("right");
        assertThat(hasher.verify("wrong", hash)).isFalse();
    }

    @Test
    void verify_emptyOrNullInputs_false() {
        String hash = hasher.hash("x");
        assertThat(hasher.verify(null, hash)).isFalse();
        assertThat(hasher.verify("x", null)).isFalse();
        assertThat(hasher.verify("x", "")).isFalse();
    }

    @Test
    void verify_corruptHash_false() {
        assertThat(hasher.verify("x", "not-a-bcrypt-hash")).isFalse();
    }
}
