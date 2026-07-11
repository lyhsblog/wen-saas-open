package com.wensaas.core.api.notification;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.wensaas.core.api.notification.send.DeliveryResult;
import com.wensaas.core.api.notification.send.MessageStatus;

class DeliveryResultTest {

    @Test
    void factories_carryStatus() {
        assertThat(DeliveryResult.sent().status()).isEqualTo(MessageStatus.SENT);
        assertThat(DeliveryResult.failed("oops").status()).isEqualTo(MessageStatus.FAILED);
        assertThat(DeliveryResult.failed("oops").errorReason()).isEqualTo("oops");
        assertThat(DeliveryResult.skipped("no phone").status()).isEqualTo(MessageStatus.SKIPPED);
    }

    @Test
    void pendingStatus_rejected() {
        assertThatThrownBy(() -> new DeliveryResult(MessageStatus.PENDING, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
