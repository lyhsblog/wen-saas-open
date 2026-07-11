package com.wensaas.core.api.notification;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import com.wensaas.core.api.notification.send.Channel;

class ChannelTest {

    @Test
    void enumNames_areStable() {
        assertThat(Channel.STATION.name()).isEqualTo("STATION");
        assertThat(Channel.EMAIL.name()).isEqualTo("EMAIL");
        assertThat(Channel.SMS.name()).isEqualTo("SMS");
        assertThat(Channel.WECHAT.name()).isEqualTo("WECHAT");
        assertThat(Channel.WECOM.name()).isEqualTo("WECOM");
        assertThat(Channel.DINGTALK.name()).isEqualTo("DINGTALK");
    }

    @Test
    void isIm_onlyImChannels() {
        assertThat(Channel.WECHAT.isIm()).isTrue();
        assertThat(Channel.WECOM.isIm()).isTrue();
        assertThat(Channel.DINGTALK.isIm()).isTrue();
        assertThat(Channel.STATION.isIm()).isFalse();
        assertThat(Channel.EMAIL.isIm()).isFalse();
        assertThat(Channel.SMS.isIm()).isFalse();
    }
}
