package com.wensaas.core.api.event;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventEnvelopeTest {

    @Test
    void happyPath() {
        Instant t = Instant.now();
        EventEnvelope e = new EventEnvelope(1L, "crm.customer_created", "evt-1", t,
                Map.of("id", 100), "req-1");
        assertThat(e.tenantId()).isEqualTo(1L);
        assertThat(e.eventType()).isEqualTo("crm.customer_created");
        assertThat(e.eventId()).isEqualTo("evt-1");
        assertThat(e.occurredAt()).isEqualTo(t);
        assertThat(e.requestId()).isEqualTo("req-1");
    }

    @Test
    void blankEventTypeRejected() {
        assertThatThrownBy(() -> new EventEnvelope(0L, "  ", "evt", Instant.now(), null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullEventIdRejected() {
        assertThatThrownBy(() -> new EventEnvelope(0L, "tenant.opened", null, Instant.now(), null, null))
                .isInstanceOf(NullPointerException.class);
    }
}
