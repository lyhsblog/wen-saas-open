package com.wensaas.core.api.notification;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.wensaas.core.api.notification.send.Channel;
import com.wensaas.core.api.notification.send.MessageCategory;
import com.wensaas.core.api.notification.send.MessageSendRequest;
import com.wensaas.core.api.notification.send.Priority;
import com.wensaas.core.api.notification.send.RecipientSpec;

class MessageSendRequestTest {

    @Test
    void builder_minimal_ok() {
        MessageSendRequest req = MessageSendRequest.builder("sys.welcome")
                .recipients(List.of(RecipientSpec.member(101L)))
                .build();
        assertThat(req.templateCode()).isEqualTo("sys.welcome");
        assertThat(req.variables()).isEmpty();
        assertThat(req.channels()).isEmpty();
        assertThat(req.noStationFallback()).isFalse();
        assertThat(req.category()).isEqualTo(MessageCategory.BUSINESS);
    }

    @Test
    void builder_full_ok() {
        MessageSendRequest req = MessageSendRequest.builder("crm.follow_reminder")
                .variables(Map.of("customer_name", "王某某", "days", 30))
                .recipients(List.of(RecipientSpec.member(201L), RecipientSpec.role(9L)))
                .channels(List.of(Channel.EMAIL))
                .related("Customer", "C-123")
                .link("/crm/customer/C-123")
                .priority(Priority.IMPORTANT)
                .category(MessageCategory.TODO)
                .build();
        assertThat(req.channels()).containsExactly(Channel.EMAIL);
        assertThat(req.relatedType()).isEqualTo("Customer");
        assertThat(req.priority()).isEqualTo(Priority.IMPORTANT);
        assertThat(req.category()).isEqualTo(MessageCategory.TODO);
    }

    @Test
    void emptyTemplateCode_rejected() {
        assertThatThrownBy(() -> new MessageSendRequest("", null,
                List.of(RecipientSpec.member(1L)), null, false, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void emptyRecipients_rejected() {
        assertThatThrownBy(() -> MessageSendRequest.builder("sys.welcome").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("收件人");
    }

    @Test
    void nullVariablesChannelsCategory_normalized() {
        MessageSendRequest req = new MessageSendRequest(
                "sys.welcome", null, List.of(RecipientSpec.member(1L)), null,
                false, null, null, null, null, null);
        assertThat(req.variables()).isEmpty();
        assertThat(req.channels()).isEmpty();
        assertThat(req.category()).isEqualTo(MessageCategory.BUSINESS);
    }
}
