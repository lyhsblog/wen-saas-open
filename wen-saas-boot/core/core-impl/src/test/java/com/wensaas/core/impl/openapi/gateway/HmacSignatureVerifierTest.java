package com.wensaas.core.impl.openapi.gateway;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HmacSignatureVerifierTest {

    private final HmacSignatureVerifier v = new HmacSignatureVerifier();

    @Test
    void sameInputsProduceSameSignature() {
        String s1 = v.sign("secret", "POST", "/x", "{\"a\":1}", 100L, "n1");
        String s2 = v.sign("secret", "POST", "/x", "{\"a\":1}", 100L, "n1");
        assertThat(s1).isEqualTo(s2).isNotBlank();
    }

    @Test
    void differentBodyChangesSignature() {
        String s1 = v.sign("secret", "POST", "/x", "{\"a\":1}", 100L, "n1");
        String s2 = v.sign("secret", "POST", "/x", "{\"a\":2}", 100L, "n1");
        assertThat(s1).isNotEqualTo(s2);
    }

    @Test
    void differentPathChangesSignature() {
        String s1 = v.sign("secret", "POST", "/x", "", 100L, "n1");
        String s2 = v.sign("secret", "POST", "/y", "", 100L, "n1");
        assertThat(s1).isNotEqualTo(s2);
    }

    @Test
    void differentTimestampChangesSignature() {
        String s1 = v.sign("secret", "POST", "/x", "", 100L, "n1");
        String s2 = v.sign("secret", "POST", "/x", "", 101L, "n1");
        assertThat(s1).isNotEqualTo(s2);
    }

    @Test
    void differentNonceChangesSignature() {
        String s1 = v.sign("secret", "POST", "/x", "", 100L, "n1");
        String s2 = v.sign("secret", "POST", "/x", "", 100L, "n2");
        assertThat(s1).isNotEqualTo(s2);
    }

    @Test
    void differentSecretChangesSignature() {
        String s1 = v.sign("secret-a", "POST", "/x", "", 100L, "n1");
        String s2 = v.sign("secret-b", "POST", "/x", "", 100L, "n1");
        assertThat(s1).isNotEqualTo(s2);
    }

    @Test
    void verifyMatch() {
        String s = v.sign("secret", "POST", "/x", "body", 100L, "n1");
        assertThat(v.verify(s, s)).isTrue();
    }

    @Test
    void verifyMismatch() {
        String s = v.sign("secret", "POST", "/x", "body", 100L, "n1");
        assertThat(v.verify(s, s + "x")).isFalse();
        assertThat(v.verify(s, null)).isFalse();
        assertThat(v.verify(null, s)).isFalse();
    }

    @Test
    void nullBodyTreatedAsEmpty() {
        String s1 = v.sign("secret", "POST", "/x", null, 100L, "n1");
        String s2 = v.sign("secret", "POST", "/x", "", 100L, "n1");
        assertThat(s1).isEqualTo(s2);
    }

    @Test
    void nullNonceTreatedAsEmpty() {
        String s1 = v.sign("secret", "POST", "/x", "body", 100L, null);
        String s2 = v.sign("secret", "POST", "/x", "body", 100L, "");
        assertThat(s1).isEqualTo(s2);
    }
}
