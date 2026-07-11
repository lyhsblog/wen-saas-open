package com.wensaas.core.api.spi.def;

import com.wensaas.core.api.notification.send.Channel;
import com.wensaas.core.api.notification.send.Priority;
import com.wensaas.core.api.notification.template.TemplateContent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationTemplateDefTest {

    @Test
    void of_factoryUsesDefaults() {
        NotificationTemplateDef def = NotificationTemplateDef.of("crm.welcome", "欢迎");
        assertThat(def.code()).isEqualTo("crm.welcome");
        assertThat(def.name()).isEqualTo("欢迎");
        assertThat(def.defaultChannels()).isEmpty();
        assertThat(def.contents()).isEmpty();
        assertThat(def.priority()).isEqualTo(Priority.NORMAL);
    }

    @Test
    void full_record_ok() {
        NotificationTemplateDef def = new NotificationTemplateDef(
                "crm.follow_reminder",
                "客户跟进提醒",
                "客户超过 N 天未跟进时",
                List.of(Channel.STATION, Channel.EMAIL),
                Map.of(
                        Channel.STATION, TemplateContent.of("客户 {customer_name} 已 {days} 天未跟进", "详情..."),
                        Channel.EMAIL, TemplateContent.of("客户 {customer_name} 已 {days} 天未跟进", "邮件正文...")
                ),
                List.of(),
                Priority.IMPORTANT);
        assertThat(def.defaultChannels()).hasSize(2);
        assertThat(def.priority()).isEqualTo(Priority.IMPORTANT);
    }

    @Test
    void defaultChannels_must_haveContents() {
        // STATION 在 defaultChannels 但 contents 没提供 → 拒绝
        assertThatThrownBy(() -> new NotificationTemplateDef(
                "crm.x", "x", null,
                List.of(Channel.STATION),
                Map.of(Channel.EMAIL, TemplateContent.of("e", "e")),
                List.of(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("STATION");
    }

    @Test
    void blank_code_rejected() {
        assertThatThrownBy(() -> new NotificationTemplateDef("", "n", null, List.of(), Map.of(), List.of(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
