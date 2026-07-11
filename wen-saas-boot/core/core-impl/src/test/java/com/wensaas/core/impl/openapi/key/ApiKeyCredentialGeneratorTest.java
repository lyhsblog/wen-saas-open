package com.wensaas.core.impl.openapi.key;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyCredentialGeneratorTest {

    @Test
    void apiKeyStartsWithPrefix() {
        ApiKeyCredentialGenerator.GeneratedCredential c = ApiKeyCredentialGenerator.generate();
        assertThat(c.apiKey()).startsWith("ak_");
        assertThat(c.apiKey().length()).isGreaterThanOrEqualTo(20);
    }

    @Test
    void plainSecretHasReasonableLength() {
        ApiKeyCredentialGenerator.GeneratedCredential c = ApiKeyCredentialGenerator.generate();
        // 32 字节 Base64-URL = 43 字符（无 padding）
        assertThat(c.plainSecret().length()).isBetween(40, 50);
    }

    @Test
    void uniquenessAcrossCalls() {
        var c1 = ApiKeyCredentialGenerator.generate();
        var c2 = ApiKeyCredentialGenerator.generate();
        assertThat(c1.apiKey()).isNotEqualTo(c2.apiKey());
        assertThat(c1.plainSecret()).isNotEqualTo(c2.plainSecret());
    }
}
