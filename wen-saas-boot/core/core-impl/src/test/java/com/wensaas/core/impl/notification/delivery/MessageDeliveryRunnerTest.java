package com.wensaas.core.impl.notification.delivery;

import com.alibaba.fastjson2.JSON;
import com.wensaas.core.api.notification.send.Channel;
import com.wensaas.core.api.notification.send.ChannelAdapter;
import com.wensaas.core.api.notification.send.DeliveryResult;
import com.wensaas.core.api.notification.send.MessageCategory;
import com.wensaas.core.api.notification.send.NotificationCategory;
import com.wensaas.core.api.notification.NotificationPreferenceService;
import com.wensaas.core.api.notification.send.Priority;
import com.wensaas.core.api.idempotent.IdempotencyGuard;
import com.wensaas.core.api.auth.user.UserDeliveryContactQuery;
import com.wensaas.core.api.auth.user.UserDeliveryContactQuery.UserContact;
import com.wensaas.core.impl.tenant.web.TenantActivityGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.wensaas.core.impl.notification.NotificationProperties;

class MessageDeliveryRunnerTest {

    private SysMessageTaskMapper taskMapper;
    private UserDeliveryContactQuery deliveryContacts;
    private NotificationPublisher publisher;
    private NotificationProperties props;
    private ChannelAdapter stationAdapter;
    private NotificationPreferenceService preferenceService;
    private IdempotencyGuard idempotency;
    private MessageDeliveryRunner runner;

    @BeforeEach
    void setUp() {
        taskMapper = mock(SysMessageTaskMapper.class);
        deliveryContacts = mock(UserDeliveryContactQuery.class);
        publisher = mock(NotificationPublisher.class);
        props = new NotificationProperties();
        props.setRetryTimes(3);
        props.setRetryBackoff(new Duration[] {
                Duration.ofSeconds(1), Duration.ofSeconds(5), Duration.ofSeconds(30)
        });
        stationAdapter = mock(ChannelAdapter.class);
        when(stationAdapter.channel()).thenReturn(Channel.STATION);
        Map<Channel, ChannelAdapter> adapters = new HashMap<>();
        adapters.put(Channel.STATION, stationAdapter);
        TenantActivityGuard activityGuard = mock(TenantActivityGuard.class);
        when(activityGuard.isActive(any())).thenReturn(true);
        preferenceService = mock(NotificationPreferenceService.class);
        idempotency = mock(IdempotencyGuard.class);
        // 默认非重复：回放 action 并返回 true（让既有用例的投递逻辑照常执行）
        when(idempotency.runOnce(any(), any(), any(), any())).thenAnswer(inv -> {
            inv.<Runnable>getArgument(3).run();
            return true;
        });
        runner = new MessageDeliveryRunner(adapters, taskMapper, deliveryContacts, publisher, props,
                activityGuard, preferenceService, idempotency);
    }

    @Test
    void deliver_success_marksSent() {
        MessageEnvelope env = newEnvelope(101L, Channel.STATION, 1);
        when(deliveryContacts.contactForDelivery(101L)).thenReturn(stubUser(101L, 9001L, "u@x", "13000000000"));
        when(stationAdapter.canDeliver(any())).thenReturn(true);
        when(stationAdapter.deliver(any(), any())).thenReturn(DeliveryResult.sent());

        runner.handle(JSON.toJSONString(env));

        verify(taskMapper).markSent(eq(env.msgTaskId()), eq(1), any(LocalDateTime.class));
        verify(publisher, never()).republish(any(), anyLong());
    }

    @Test
    void deliver_noAdapter_marksFailedTerminal() {
        // 用一个没在 adapter map 中的 channel
        MessageEnvelope env = newEnvelope(101L, Channel.EMAIL, 1);
        runner.handle(JSON.toJSONString(env));
        verify(taskMapper).markFailed(eq(env.msgTaskId()), eq(1), anyString(), eq(true));
    }

    @Test
    void deliver_recipient_notFound_markedSkipped() {
        MessageEnvelope env = newEnvelope(404L, Channel.STATION, 1);
        when(deliveryContacts.contactForDelivery(404L)).thenReturn(null);
        runner.handle(JSON.toJSONString(env));
        verify(taskMapper).markSkipped(eq(env.msgTaskId()), eq("recipient not found"));
    }

    @Test
    void deliver_cannotDeliver_markedSkipped() {
        MessageEnvelope env = newEnvelope(101L, Channel.STATION, 1);
        when(deliveryContacts.contactForDelivery(101L)).thenReturn(stubUser(101L, 9001L, null, null));
        when(stationAdapter.canDeliver(any())).thenReturn(false);
        runner.handle(JSON.toJSONString(env));
        verify(taskMapper).markSkipped(eq(env.msgTaskId()), anyString());
    }

    @Test
    void deliver_failure_attempt1_republish_with_backoff_1s() {
        MessageEnvelope env = newEnvelope(101L, Channel.STATION, 1);
        when(deliveryContacts.contactForDelivery(101L)).thenReturn(stubUser(101L, 9001L, "u@x", null));
        when(stationAdapter.canDeliver(any())).thenReturn(true);
        when(stationAdapter.deliver(any(), any())).thenReturn(DeliveryResult.failed("boom"));

        runner.handle(JSON.toJSONString(env));

        verify(taskMapper).markFailed(eq(env.msgTaskId()), eq(1), eq("boom"), eq(false));
        ArgumentCaptor<MessageEnvelope> envCap = ArgumentCaptor.forClass(MessageEnvelope.class);
        ArgumentCaptor<Long> delayCap = ArgumentCaptor.forClass(Long.class);
        verify(publisher).republish(envCap.capture(), delayCap.capture());
        assertThat(envCap.getValue().attempt()).isEqualTo(2);
        assertThat(delayCap.getValue()).isEqualTo(1000L);
    }

    @Test
    void deliver_failure_attempt2_uses_backoff_5s() {
        MessageEnvelope env = newEnvelope(101L, Channel.STATION, 2);
        when(deliveryContacts.contactForDelivery(101L)).thenReturn(stubUser(101L, 9001L, "u@x", null));
        when(stationAdapter.canDeliver(any())).thenReturn(true);
        when(stationAdapter.deliver(any(), any())).thenReturn(DeliveryResult.failed("boom"));
        runner.handle(JSON.toJSONString(env));
        ArgumentCaptor<Long> delayCap = ArgumentCaptor.forClass(Long.class);
        verify(publisher).republish(any(), delayCap.capture());
        assertThat(delayCap.getValue()).isEqualTo(5_000L);
    }

    @Test
    void deliver_failure_attemptMax_marks_failed_terminal_noRepublish() {
        MessageEnvelope env = newEnvelope(101L, Channel.STATION, 3);  // attempt == retryTimes
        when(deliveryContacts.contactForDelivery(101L)).thenReturn(stubUser(101L, 9001L, "u@x", null));
        when(stationAdapter.canDeliver(any())).thenReturn(true);
        when(stationAdapter.deliver(any(), any())).thenReturn(DeliveryResult.failed("boom"));

        runner.handle(JSON.toJSONString(env));

        verify(taskMapper).markFailed(eq(env.msgTaskId()), eq(3), eq("boom"), eq(true));
        verify(publisher, never()).republish(any(), anyLong());
    }

    @Test
    void deliver_adapter_throws_treated_as_failed_with_retry() {
        MessageEnvelope env = newEnvelope(101L, Channel.STATION, 1);
        when(deliveryContacts.contactForDelivery(101L)).thenReturn(stubUser(101L, 9001L, "u@x", null));
        when(stationAdapter.canDeliver(any())).thenReturn(true);
        when(stationAdapter.deliver(any(), any())).thenThrow(new RuntimeException("net"));

        runner.handle(JSON.toJSONString(env));

        verify(taskMapper).markFailed(eq(env.msgTaskId()), eq(1), anyString(), eq(false));
        verify(publisher).republish(any(), eq(1000L));
    }

    @Test
    void deliver_userPreferenceOff_marksSkipped_andNoDeliver() {
        ChannelAdapter emailAdapter = mock(ChannelAdapter.class);
        when(emailAdapter.channel()).thenReturn(Channel.EMAIL);
        when(emailAdapter.canDeliver(any())).thenReturn(true);
        Map<Channel, ChannelAdapter> adapters = new HashMap<>();
        adapters.put(Channel.EMAIL, emailAdapter);
        TenantActivityGuard activityGuard = mock(TenantActivityGuard.class);
        when(activityGuard.isActive(any())).thenReturn(true);
        MessageDeliveryRunner r = new MessageDeliveryRunner(adapters, taskMapper, deliveryContacts,
                publisher, props, activityGuard, preferenceService, idempotency);

        MessageEnvelope env = newEnvelope(101L, Channel.EMAIL, NotificationCategory.PACKAGE_EXPIRY, 1);
        when(deliveryContacts.contactForDelivery(101L)).thenReturn(stubUser(101L, 9001L, "u@x", "13000000000"));
        when(preferenceService.isChannelEnabled(9001L, 101L, NotificationCategory.PACKAGE_EXPIRY, Channel.EMAIL))
                .thenReturn(false);

        r.handle(JSON.toJSONString(env));

        verify(taskMapper).markSkipped(env.msgTaskId(), "user-preference-off");
        verify(emailAdapter, never()).deliver(any(), any());
    }

    @Test
    void deliver_stationChannel_neverFilteredByPreference() {
        // STATION 强制送达：即使带类别，也不查偏好，正常投递
        MessageEnvelope env = newEnvelope(101L, Channel.STATION, NotificationCategory.SECURITY_ALERT, 1);
        when(deliveryContacts.contactForDelivery(101L)).thenReturn(stubUser(101L, 9001L, "u@x", "13000000000"));
        when(stationAdapter.canDeliver(any())).thenReturn(true);
        when(stationAdapter.deliver(any(), any())).thenReturn(DeliveryResult.sent());

        runner.handle(JSON.toJSONString(env));

        verify(taskMapper).markSent(eq(env.msgTaskId()), eq(1), any(LocalDateTime.class));
        verify(preferenceService, never()).isChannelEnabled(anyLong(), anyLong(), any(), any());
    }

    @Test
    void handle_usesDedupKey_channelMsgTaskIdAttempt() {
        MessageEnvelope env = newEnvelope(101L, Channel.STATION, 1);
        when(deliveryContacts.contactForDelivery(101L)).thenReturn(stubUser(101L, 9001L, "u@x", null));
        when(stationAdapter.canDeliver(any())).thenReturn(true);
        when(stationAdapter.deliver(any(), any())).thenReturn(DeliveryResult.sent());

        runner.handle(JSON.toJSONString(env));

        // 去重域 notify-delivery + 键 STATION:7777:1（channel:msgTaskId:attempt）
        verify(idempotency).runOnce(eq("notify-delivery"), eq("STATION:7777:1"), any(), any());
    }

    @Test
    void handle_duplicateMessage_skipsDelivery() {
        // 模拟重复投递：runOnce 判重返回 false 且不回放 action
        // 用 doReturn 覆盖既有 thenAnswer，避免 when() 二次触发旧 answer（getArgument 为 null 抛 NPE）
        doReturn(false).when(idempotency).runOnce(any(), any(), any(), any());
        MessageEnvelope env = newEnvelope(101L, Channel.STATION, 1);

        runner.handle(JSON.toJSONString(env));

        // 不投递、不写终态
        verify(stationAdapter, never()).deliver(any(), any());
        verify(taskMapper, never()).markSent(anyLong(), anyInt(), any());
        verify(taskMapper, never()).markFailed(anyLong(), anyInt(), anyString(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    private static MessageEnvelope newEnvelope(long recipientId, Channel channel, int attempt) {
        return newEnvelope(recipientId, channel, null, attempt);
    }

    private static MessageEnvelope newEnvelope(long recipientId, Channel channel,
                                               NotificationCategory notificationCategory, int attempt) {
        return new MessageEnvelope(
                7777L, "nt-1", 9001L, channel, recipientId,
                "sys.welcome", MessageCategory.SYSTEM, notificationCategory, Priority.NORMAL,
                "title", "body", null, null, null, "sys",
                Map.of(), "req-1", attempt);
    }

    private static UserContact stubUser(long id, long tenantId, String email, String mobile) {
        return new UserContact(id, tenantId, email, mobile);
    }
}
