package com.wensaas.core.impl.webhook.dispatch;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookSignatureSignerTest {

    private final WebhookSignatureSigner signer = new WebhookSignatureSigner();

    @Test
    void deterministicForSameInputs() {
        String s1 = signer.sign("secret", "crm.customer_created", 100L, "{\"id\":1}");
        String s2 = signer.sign("secret", "crm.customer_created", 100L, "{\"id\":1}");
        assertThat(s1).isEqualTo(s2).isNotBlank();
    }

    @Test
    void differentEventChangesSignature() {
        String s1 = signer.sign("secret", "a.event", 100L, "{}");
        String s2 = signer.sign("secret", "b.event", 100L, "{}");
        assertThat(s1).isNotEqualTo(s2);
    }

    @Test
    void differentSecretChangesSignature() {
        String s1 = signer.sign("secret-a", "a.event", 100L, "{}");
        String s2 = signer.sign("secret-b", "a.event", 100L, "{}");
        assertThat(s1).isNotEqualTo(s2);
    }

    @Test
    void nullBodyTreatedAsEmpty() {
        String s1 = signer.sign("k", "evt", 1L, null);
        String s2 = signer.sign("k", "evt", 1L, "");
        assertThat(s1).isEqualTo(s2);
    }
}
