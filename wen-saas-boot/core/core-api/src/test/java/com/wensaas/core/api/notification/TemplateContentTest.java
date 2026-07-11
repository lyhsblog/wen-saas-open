package com.wensaas.core.api.notification;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.wensaas.core.api.notification.template.TemplateContent;

class TemplateContentTest {

    @Test
    void of_body_only_ok() {
        TemplateContent c = TemplateContent.of("正文");
        assertThat(c.title()).isNull();
        assertThat(c.body()).isEqualTo("正文");
    }

    @Test
    void of_title_body_ok() {
        TemplateContent c = TemplateContent.of("标题", "正文");
        assertThat(c.title()).isEqualTo("标题");
        assertThat(c.body()).isEqualTo("正文");
    }

    @Test
    void blank_body_rejected() {
        assertThatThrownBy(() -> new TemplateContent("t", "  "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
