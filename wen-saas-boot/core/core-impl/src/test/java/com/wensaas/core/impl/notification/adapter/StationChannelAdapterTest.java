package com.wensaas.core.impl.notification.adapter;

import com.wensaas.core.api.notification.send.Channel;
import com.wensaas.core.api.notification.send.MessageCategory;
import com.wensaas.core.api.notification.send.MessageStatus;
import com.wensaas.core.api.notification.send.Priority;
import com.wensaas.core.api.notification.send.Recipient;
import com.wensaas.core.api.notification.template.RenderedMessage;
import com.wensaas.core.impl.notification.inbox.SysStationMessage;
import com.wensaas.core.impl.notification.inbox.SysStationMessageMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class StationChannelAdapterTest {

    @Test
    void canDeliver_alwaysTrue() {
        StationChannelAdapter adapter = new StationChannelAdapter(mock(SysStationMessageMapper.class));
        assertThat(adapter.canDeliver(new Recipient(1L, 9L, null, null))).isTrue();
        assertThat(adapter.channel()).isEqualTo(Channel.STATION);
    }

    @Test
    void deliver_writesStationRow_returnsSent() {
        SysStationMessageMapper mapper = mock(SysStationMessageMapper.class);
        StationChannelAdapter adapter = new StationChannelAdapter(mapper);
        RenderedMessage msg = new RenderedMessage(
                9001L, "nt-1", Channel.STATION, "crm.welcome",
                MessageCategory.BUSINESS, Priority.IMPORTANT,
                "标题", "正文",
                "/crm/customer/1", "Customer", "1", "crm", Map.of());
        Recipient r = new Recipient(101L, 9001L, "u@example.com", "13000000000");

        assertThat(adapter.deliver(msg, r).status()).isEqualTo(MessageStatus.SENT);

        ArgumentCaptor<SysStationMessage> cap = ArgumentCaptor.forClass(SysStationMessage.class);
        verify(mapper).insert(cap.capture());
        SysStationMessage row = cap.getValue();
        assertThat(row.getTenantId()).isEqualTo(9001L);
        assertThat(row.getRecipientId()).isEqualTo(101L);
        assertThat(row.getCategory()).isEqualTo("BUSINESS");
        assertThat(row.getTitle()).isEqualTo("标题");
        assertThat(row.getBody()).isEqualTo("正文");
        assertThat(row.getLink()).isEqualTo("/crm/customer/1");
        assertThat(row.getPriority()).isEqualTo("IMPORTANT");
        assertThat(row.getTemplateCode()).isEqualTo("crm.welcome");
        assertThat(row.getSourceModule()).isEqualTo("crm");
        assertThat(row.getCreatedAt()).isNotNull();
    }
}
