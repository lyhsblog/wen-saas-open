package com.wensaas.core.web.file.dto;

/**
 * 消费分享（带密码）的 REST 入参。无密码分享时 body 可为 {@code {}} 或不传。
 */
public record ConsumeShareRequestDto(String password) {

    public static ConsumeShareRequestDto empty() {
        return new ConsumeShareRequestDto(null);
    }
}
