package com.wensaas.core.impl.notification.adapter;

import com.wensaas.core.api.notification.send.Channel;
import com.wensaas.core.api.notification.send.MessageCategory;
import com.wensaas.core.api.notification.send.MessageStatus;
import com.wensaas.core.api.notification.send.Priority;
import com.wensaas.core.api.notification.send.Recipient;
import com.wensaas.core.api.notification.template.RenderedMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailChannelAdapterTest {

    private final RenderedMessage msg = new RenderedMessage(
            9001L, "nt-1", Channel.EMAIL, "sys.welcome",
            MessageCategory.SYSTEM, Priority.NORMAL,
            "Welcome", "Hi there", null, null, null, "sys", Map.of());

    @Test
    void canDeliver_needsEmail() {
        EmailChannelAdapter a = new EmailChannelAdapter(mock(JavaMailSender.class), true, null, null, 0, List.of());
        assertThat(a.canDeliver(new Recipient(1L, 9L, null, null))).isFalse();
        assertThat(a.canDeliver(new Recipient(1L, 9L, "  ", null))).isFalse();
        assertThat(a.canDeliver(new Recipient(1L, 9L, "u@x.com", null))).isTrue();
        assertThat(a.channel()).isEqualTo(Channel.EMAIL);
    }

    @Test
    void noop_doesNotInvokeSender_returnsSent() {
        JavaMailSender sender = mock(JavaMailSender.class);
        EmailChannelAdapter a = new EmailChannelAdapter(sender, true, "noreply@example.com", null, 0, List.of());
        assertThat(a.deliver(msg, new Recipient(1L, 9L, "u@x.com", null)).status())
                .isEqualTo(MessageStatus.SENT);
        verify(sender, never()).send((SimpleMailMessage) null);
    }

    @Test
    void real_send_invokesMailSender_withTitleAndBody() {
        JavaMailSender sender = mock(JavaMailSender.class);
        // dailyLimit=0 关闭当日闸，不触碰 redis（传 null 亦安全）
        EmailChannelAdapter a = new EmailChannelAdapter(sender, false, "noreply@example.com", null, 0, List.of());
        a.deliver(msg, new Recipient(1L, 9L, "u@x.com", null));
        ArgumentCaptor<SimpleMailMessage> cap = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(cap.capture());
        SimpleMailMessage sent = cap.getValue();
        assertThat(sent.getFrom()).isEqualTo("noreply@example.com");
        assertThat(sent.getTo()).containsExactly("u@x.com");
        assertThat(sent.getSubject()).isEqualTo("Welcome");
        assertThat(sent.getText()).isEqualTo("Hi there");
    }

    @Test
    void blacklist_matchesCaseInsensitive_skipsSendAsSent() {
        JavaMailSender sender = mock(JavaMailSender.class);
        EmailChannelAdapter a = new EmailChannelAdapter(sender, false, "noreply@example.com",
                null, 0, List.of("Blocked@Example.com"));
        assertThat(a.deliver(msg, new Recipient(1L, 9L, "blocked@example.com", null)).status())
                .isEqualTo(MessageStatus.SENT);
        verify(sender, never()).send(any(SimpleMailMessage.class));
        // 未命中黑名单的地址正常发送
        a.deliver(msg, new Recipient(1L, 9L, "ok@example.com", null));
        verify(sender).send(any(SimpleMailMessage.class));
    }

    @Test
    void dailyLimit_blocksWhenExceeded_doesNotSend() {
        JavaMailSender sender = mock(JavaMailSender.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.increment(anyString())).thenReturn(101L); // 占位后 > limit(100)
        EmailChannelAdapter a = new EmailChannelAdapter(sender, false, "noreply@example.com", redis, 100, List.of());
        assertThat(a.deliver(msg, new Recipient(1L, 9L, "u@x.com", null)).status())
                .isEqualTo(MessageStatus.FAILED);
        verify(sender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void dailyLimit_allowsWithinCap_sends() {
        JavaMailSender sender = mock(JavaMailSender.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.increment(anyString())).thenReturn(1L); // 首封，占位 <= limit
        EmailChannelAdapter a = new EmailChannelAdapter(sender, false, "noreply@example.com", redis, 100, List.of());
        assertThat(a.deliver(msg, new Recipient(1L, 9L, "u@x.com", null)).status())
                .isEqualTo(MessageStatus.SENT);
        verify(sender).send(any(SimpleMailMessage.class));
    }
}
