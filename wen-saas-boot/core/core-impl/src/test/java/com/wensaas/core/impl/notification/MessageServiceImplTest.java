package com.wensaas.core.impl.notification;

import com.wensaas.core.api.notification.send.Channel;
import com.wensaas.core.api.notification.send.MessageSendRequest;
import com.wensaas.core.api.notification.send.MessageTaskId;
import com.wensaas.core.api.notification.send.NotificationException;
import com.wensaas.core.api.notification.send.Priority;
import com.wensaas.core.api.notification.send.Recipient;
import com.wensaas.core.api.notification.send.RecipientSpec;
import com.wensaas.core.api.notification.template.TemplateContent;
import com.wensaas.core.api.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.wensaas.core.impl.notification.delivery.MessageEnvelope;
import com.wensaas.core.impl.notification.delivery.NotificationPublisher;
import com.wensaas.core.impl.notification.delivery.RecipientResolver;
import com.wensaas.core.impl.notification.delivery.SysMessageTask;
import com.wensaas.core.impl.notification.delivery.SysMessageTaskMapper;
import com.wensaas.core.impl.notification.template.TemplateResolver;
import com.wensaas.core.impl.notification.template.TemplateSnapshot;

class MessageServiceImplTest {

    private TemplateResolver templateResolver;
    private RecipientResolver recipientResolver;
    private NotificationPublisher publisher;
    private SysMessageTaskMapper taskMapper;
    private MessageServiceImpl service;

    private final TemplateSnapshot stationOnly = new TemplateSnapshot(
            0L, "crm.welcome", "crm",
            List.of(Channel.STATION),
            Map.of(Channel.STATION, TemplateContent.of("欢迎 {name}", "{name}，欢迎使用本系统")),
            Priority.NORMAL, null);

    private final TemplateSnapshot stationAndEmail = new TemplateSnapshot(
            0L, "crm.welcome", "crm",
            List.of(Channel.STATION, Channel.EMAIL),
            Map.of(
                    Channel.STATION, TemplateContent.of("欢迎", "正文"),
                    Channel.EMAIL, TemplateContent.of("欢迎", "邮件正文")),
            Priority.IMPORTANT, null);

    @BeforeEach
    void setUp() {
        templateResolver = mock(TemplateResolver.class);
        recipientResolver = mock(RecipientResolver.class);
        publisher = mock(NotificationPublisher.class);
        taskMapper = mock(SysMessageTaskMapper.class);
        service = new MessageServiceImpl(templateResolver, recipientResolver, publisher, taskMapper);
        TenantContext.set(9001L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void send_template_not_found_throws() {
        when(templateResolver.resolve(9001L, "missing")).thenReturn(Optional.empty());
        MessageSendRequest req = MessageSendRequest.builder("missing")
                .recipients(List.of(RecipientSpec.member(101L)))
                .build();
        assertThatThrownBy(() -> service.send(req))
                .isInstanceOf(NotificationException.class)
                .extracting("code").isEqualTo(NotificationException.Code.TEMPLATE_NOT_FOUND);
    }

    @Test
    void send_no_recipient_throws() {
        when(templateResolver.resolve(9001L, "crm.welcome")).thenReturn(Optional.of(stationOnly));
        when(recipientResolver.resolve(any())).thenReturn(List.of());
        MessageSendRequest req = MessageSendRequest.builder("crm.welcome")
                .recipients(List.of(RecipientSpec.member(404L)))
                .build();
        assertThatThrownBy(() -> service.send(req))
                .isInstanceOf(NotificationException.class)
                .extracting("code").isEqualTo(NotificationException.Code.NO_RECIPIENT_RESOLVED);
    }

    @Test
    void send_template_channel_missing_throws() {
        // 模板只有 STATION，请求显式要 EMAIL（未提供文案）→ 抛 TEMPLATE_CHANNEL_MISSING
        when(templateResolver.resolve(9001L, "crm.welcome")).thenReturn(Optional.of(stationOnly));
        when(recipientResolver.resolve(any())).thenReturn(List.of(new Recipient(101L, 9001L, null, null)));
        MessageSendRequest req = MessageSendRequest.builder("crm.welcome")
                .recipients(List.of(RecipientSpec.member(101L)))
                .channels(List.of(Channel.EMAIL))
                .noStationFallback(true)
                .build();
        assertThatThrownBy(() -> service.send(req))
                .isInstanceOf(NotificationException.class)
                .extracting("code").isEqualTo(NotificationException.Code.TEMPLATE_CHANNEL_MISSING);
    }

    @Test
    void send_station_fallback_added_by_default() {
        when(templateResolver.resolve(9001L, "crm.welcome")).thenReturn(Optional.of(stationAndEmail));
        when(recipientResolver.resolve(any())).thenReturn(List.of(new Recipient(101L, 9001L, "u@x", null)));
        // 请求只传 EMAIL，由于 noStationFallback=false（默认）会自动加 STATION
        MessageSendRequest req = MessageSendRequest.builder("crm.welcome")
                .recipients(List.of(RecipientSpec.member(101L)))
                .channels(List.of(Channel.EMAIL))
                .variables(Map.of())
                .build();
        MessageTaskId taskId = service.send(req);

        assertThat(taskId.value()).startsWith("nt-");
        // 应有 2 行 task（EMAIL + STATION fallback）一次批量落库 + 2 次发布
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SysMessageTask>> rowsCap = ArgumentCaptor.forClass(List.class);
        verify(taskMapper).insertBatch(rowsCap.capture());
        assertThat(rowsCap.getValue()).hasSize(2);
        ArgumentCaptor<MessageEnvelope> cap = ArgumentCaptor.forClass(MessageEnvelope.class);
        verify(publisher, times(2)).publish(cap.capture());
        assertThat(cap.getAllValues()).extracting(MessageEnvelope::channel)
                .containsExactlyInAnyOrder(Channel.EMAIL, Channel.STATION);
    }

    @Test
    void send_noStationFallback_explicit_skipsStation() {
        when(templateResolver.resolve(9001L, "crm.welcome")).thenReturn(Optional.of(stationAndEmail));
        when(recipientResolver.resolve(any())).thenReturn(List.of(new Recipient(101L, 9001L, "u@x", null)));
        MessageSendRequest req = MessageSendRequest.builder("crm.welcome")
                .recipients(List.of(RecipientSpec.member(101L)))
                .channels(List.of(Channel.EMAIL))
                .noStationFallback(true)
                .build();
        service.send(req);
        ArgumentCaptor<MessageEnvelope> cap = ArgumentCaptor.forClass(MessageEnvelope.class);
        verify(publisher).publish(cap.capture());
        assertThat(cap.getValue().channel()).isEqualTo(Channel.EMAIL);
    }

    @Test
    void send_uses_template_defaultChannels_whenRequestNotSpecified() {
        when(templateResolver.resolve(9001L, "crm.welcome")).thenReturn(Optional.of(stationAndEmail));
        when(recipientResolver.resolve(any())).thenReturn(List.of(new Recipient(101L, 9001L, "u@x", null)));
        MessageSendRequest req = MessageSendRequest.builder("crm.welcome")
                .recipients(List.of(RecipientSpec.member(101L)))
                .build();
        service.send(req);
        verify(publisher, times(2)).publish(any());
    }

    @Test
    void send_variables_rendered_into_envelope() {
        when(templateResolver.resolve(9001L, "crm.welcome")).thenReturn(Optional.of(stationOnly));
        when(recipientResolver.resolve(any())).thenReturn(List.of(new Recipient(101L, 9001L, null, null)));
        MessageSendRequest req = MessageSendRequest.builder("crm.welcome")
                .recipients(List.of(RecipientSpec.member(101L)))
                .variables(Map.of("name", "王某某"))
                .build();
        service.send(req);
        ArgumentCaptor<MessageEnvelope> cap = ArgumentCaptor.forClass(MessageEnvelope.class);
        verify(publisher).publish(cap.capture());
        MessageEnvelope env = cap.getValue();
        assertThat(env.title()).isEqualTo("欢迎 王某某");
        assertThat(env.body()).isEqualTo("王某某，欢迎使用本系统");
        assertThat(env.tenantId()).isEqualTo(9001L);
        assertThat(env.recipientId()).isEqualTo(101L);
        assertThat(env.attempt()).isEqualTo(1);
        assertThat(env.sourceModule()).isEqualTo("crm");
    }

    @Test
    void send_multipleRecipients_multipleChannels_crossProduct() {
        when(templateResolver.resolve(9001L, "crm.welcome")).thenReturn(Optional.of(stationAndEmail));
        when(recipientResolver.resolve(any())).thenReturn(List.of(
                new Recipient(101L, 9001L, "a@x", null),
                new Recipient(102L, 9001L, "b@x", null)));
        MessageSendRequest req = MessageSendRequest.builder("crm.welcome")
                .recipients(List.of(RecipientSpec.member(101L), RecipientSpec.member(102L)))
                .channels(List.of(Channel.STATION, Channel.EMAIL))
                .noStationFallback(true)
                .build();
        service.send(req);
        // 2 收件人 × 2 通道 = 4 条 task，一次批量落库 + 4 次发布
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SysMessageTask>> rowsCap = ArgumentCaptor.forClass(List.class);
        verify(taskMapper).insertBatch(rowsCap.capture());
        assertThat(rowsCap.getValue()).hasSize(4);
        verify(publisher, times(4)).publish(any());
    }

    @Test
    void status_unknownTaskId_emptyOptional() {
        when(taskMapper.selectByTaskId(anyString())).thenReturn(List.of());
        assertThat(service.status(new MessageTaskId("nt-unknown"))).isEmpty();
        verify(publisher, never()).publish(any());
    }

    @Test
    void moduleFromTemplateCode_parses() {
        assertThat(MessageServiceImpl.moduleFromTemplateCode("crm.welcome")).isEqualTo("crm");
        assertThat(MessageServiceImpl.moduleFromTemplateCode("sys.x.y")).isEqualTo("sys");
        assertThat(MessageServiceImpl.moduleFromTemplateCode("nodots")).isNull();
        assertThat(MessageServiceImpl.moduleFromTemplateCode(null)).isNull();
    }

    @Test
    void effectiveChannels_dedup_and_keepOrder() {
        TemplateSnapshot tpl = new TemplateSnapshot(0L, "x", "x",
                List.of(Channel.EMAIL, Channel.STATION),
                Map.of(Channel.EMAIL, TemplateContent.of("e","e"),
                        Channel.STATION, TemplateContent.of("s","s")),
                Priority.NORMAL, null);
        MessageSendRequest req = MessageSendRequest.builder("x")
                .recipients(List.of(RecipientSpec.member(1L)))
                .channels(List.of(Channel.EMAIL, Channel.EMAIL))
                .build();
        // request.channels 优先 + 默认追加 STATION + 去重
        assertThat(MessageServiceImpl.effectiveChannels(req, tpl))
                .containsExactly(Channel.EMAIL, Channel.STATION);
    }
}
