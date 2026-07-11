package com.wensaas.core.impl.webhook;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookPropertiesBackoffTest {

    @Test
    void defaultBackoffSequence() {
        WebhookProperties p = new WebhookProperties();
        // Plan 约定：5s / 30s / 5min
        assertThat(p.backoffFor(1)).isEqualTo(Duration.ofSeconds(5));
        assertThat(p.backoffFor(2)).isEqualTo(Duration.ofSeconds(30));
        assertThat(p.backoffFor(3)).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void overflowExtendsLastValue() {
        WebhookProperties p = new WebhookProperties();
        assertThat(p.backoffFor(99)).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void zeroAttemptClamped() {
        WebhookProperties p = new WebhookProperties();
        assertThat(p.backoffFor(0)).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    void emptyBackoffReturnsZero() {
        WebhookProperties p = new WebhookProperties();
        p.setRetryBackoff(List.of());
        assertThat(p.backoffFor(1)).isEqualTo(Duration.ZERO);
    }
}
