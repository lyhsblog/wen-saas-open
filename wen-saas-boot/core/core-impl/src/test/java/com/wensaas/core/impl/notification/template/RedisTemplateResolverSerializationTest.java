package com.wensaas.core.impl.notification.template;

import com.wensaas.core.api.notification.send.Channel;
import com.wensaas.core.api.notification.send.Priority;
import com.wensaas.core.api.notification.template.TemplateContent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RedisTemplateResolverSerializationTest {

    @Test
    void roundTrip_preservesChannelsAndContents() {
        TemplateSnapshot src = new TemplateSnapshot(
                9001L, "crm.welcome", "crm",
                List.of(Channel.STATION, Channel.EMAIL),
                Map.of(
                        Channel.STATION, TemplateContent.of("欢迎 {name}", "{name}，欢迎"),
                        Channel.EMAIL, TemplateContent.of("欢迎 {name}", "邮件正文")),
                Priority.IMPORTANT, "PACKAGE_EXPIRY");

        String json = RedisTemplateResolver.serialize(src);
        TemplateSnapshot back = RedisTemplateResolver.deserialize(json);

        assertThat(back.templateCode()).isEqualTo("crm.welcome");
        assertThat(back.tenantId()).isEqualTo(9001L);
        assertThat(back.moduleCode()).isEqualTo("crm");
        assertThat(back.priority()).isEqualTo(Priority.IMPORTANT);
        assertThat(back.notificationCategory()).isEqualTo("PACKAGE_EXPIRY");
        assertThat(back.defaultChannels()).containsExactly(Channel.STATION, Channel.EMAIL);
        assertThat(back.contents()).containsKey(Channel.STATION);
        assertThat(back.contents().get(Channel.EMAIL).body()).isEqualTo("邮件正文");
    }

    @Test
    void keyOf_format() {
        assertThat(RedisTemplateResolver.keyOf(9001L, "crm.welcome"))
                .isEqualTo("wen-saas-boot:notify:tpl:9001:crm.welcome");
    }
}
