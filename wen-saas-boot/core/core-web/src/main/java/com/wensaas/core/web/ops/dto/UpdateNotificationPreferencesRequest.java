package com.wensaas.core.web.ops.dto;

import com.wensaas.core.api.notification.send.Channel;
import com.wensaas.core.api.notification.send.NotificationCategory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * 批量修改通知偏好。STATION（站内信）通道的关闭项被后端忽略（强制开启）。
 */
public record UpdateNotificationPreferencesRequest(
        @Valid @NotNull List<Item> preferences) {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    public static class Item {
        @NotNull
        private NotificationCategory category;
        @NotNull
        private Channel channel;
        private boolean enabled;

    }
}
