package com.wensaas.core.api.notification.template;

import java.util.Objects;

/**
 * 通道文案。
 *
 * <p>title 用于站内信 / 邮件主题；短信通道通常仅用 body。
 *
 * @param title 标题（可空：短信通道场景）
 * @param body  正文
 */
public record TemplateContent(String title, String body) {

    public TemplateContent {
        Objects.requireNonNull(body, "body");
        if (body.isBlank()) {
            throw new IllegalArgumentException("body 不可空");
        }
    }

    public static TemplateContent of(String body) {
        return new TemplateContent(null, body);
    }

    public static TemplateContent of(String title, String body) {
        return new TemplateContent(title, body);
    }
}
