package com.wensaas.core.impl.notification.adapter;

import com.wensaas.core.api.notification.send.Channel;
import com.wensaas.core.api.notification.send.MessageCategory;
import com.wensaas.core.api.notification.send.MessageStatus;
import com.wensaas.core.api.notification.send.Priority;
import com.wensaas.core.api.notification.send.Recipient;
import com.wensaas.core.api.notification.template.RenderedMessage;
import com.wensaas.core.impl.notification.SmsProperties;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SmsAndImChannelAdapterTest {

    private final RenderedMessage msg = new RenderedMessage(
            9L, "nt-1", Channel.SMS, "sys.x",
            MessageCategory.SYSTEM, Priority.NORMAL,
            null, "短信内容", null, null, null, "sys", Map.of());

    @Test
    void sms_needsPhone_canDeliver() {
        SmsProperties props = new SmsProperties();
        props.setNoop(true);
        SmsChannelAdapter a = new SmsChannelAdapter(props);
        assertThat(a.canDeliver(new Recipient(1L, 9L, null, null))).isFalse();
        assertThat(a.canDeliver(new Recipient(1L, 9L, null, "13000000000"))).isTrue();
    }

    @Test
    void sms_stub_returnsSent() {
        assertThat(new SmsChannelAdapter(new SmsProperties())
                .deliver(msg, new Recipient(1L, 9L, null, "13000000000")).status())
                .isEqualTo(MessageStatus.SENT);
    }

    @Test
    void im_stubs_returnSkipped() {
        Recipient r = new Recipient(1L, 9L, null, null);
        assertThat(new ImChannelAdapters.WechatChannelAdapter().channel()).isEqualTo(Channel.WECHAT);
        assertThat(new ImChannelAdapters.WechatChannelAdapter().canDeliver(r)).isFalse();
        assertThat(new ImChannelAdapters.WechatChannelAdapter().deliver(msg, r).status())
                .isEqualTo(MessageStatus.SKIPPED);

        assertThat(new ImChannelAdapters.WecomChannelAdapter().channel()).isEqualTo(Channel.WECOM);
        assertThat(new ImChannelAdapters.WecomChannelAdapter().deliver(msg, r).status())
                .isEqualTo(MessageStatus.SKIPPED);

        assertThat(new ImChannelAdapters.DingtalkChannelAdapter().channel()).isEqualTo(Channel.DINGTALK);
        assertThat(new ImChannelAdapters.DingtalkChannelAdapter().deliver(msg, r).status())
                .isEqualTo(MessageStatus.SKIPPED);
    }
}
