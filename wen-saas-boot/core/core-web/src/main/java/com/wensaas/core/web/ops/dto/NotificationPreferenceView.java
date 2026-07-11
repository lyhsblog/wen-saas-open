package com.wensaas.core.web.ops.dto;

import com.wensaas.core.api.notification.send.Channel;
import com.wensaas.core.api.notification.send.NotificationCategory;
import com.wensaas.core.api.notification.NotificationPreferenceService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * 通知偏好网格视图：按类别分组，每组列出各通道开关。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public class NotificationPreferenceView {
    private List<CategoryGroup> categories;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    public static class CategoryGroup {
        private NotificationCategory category;
        private String label;
        private List<ChannelState> channels;

    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    public static class ChannelState {
        private Channel channel;
        private boolean enabled;
        private boolean locked;

    }

    /** 由服务返回的扁平偏好项组装为按类别分组的视图。STATION 通道标记 locked（强制开启不可改）。 */
    public static NotificationPreferenceView from(List<NotificationPreferenceService.PreferenceItem> items) {
        Map<NotificationCategory, List<ChannelState>> grouped = new LinkedHashMap<>();
        for (NotificationPreferenceService.PreferenceItem item : items) {
            grouped.computeIfAbsent(item.category(), k -> new ArrayList<>())
                    .add(new ChannelState(item.channel(), item.enabled(), item.channel() == Channel.STATION));
        }
        List<CategoryGroup> groups = new ArrayList<>();
        grouped.forEach((category, channels) ->
                groups.add(new CategoryGroup(category, category.label(), channels)));
        return new NotificationPreferenceView(groups);
    }
}
