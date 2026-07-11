package com.wensaas.core.api.notification;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import com.wensaas.core.api.notification.send.Channel;
import com.wensaas.core.api.notification.send.NotificationCategory;

/**
 * 用户通知偏好服务。按 (用户 × {@link NotificationCategory} × {@link Channel}) 维护接收开关。
 *
 * <p>缺记录 = 默认开启；
 * {@link Channel#STATION}（站内信）强制开启，不可关闭。
 */
public interface NotificationPreferenceService {

    /**
     * 投递判定：该用户在该类别该通道是否接收。
     * STATION 恒为 true；其余通道无记录时默认 true。
     */
    boolean isChannelEnabled(long tenantId, long userId, NotificationCategory category, Channel channel);

    /** 读取该用户完整偏好网格（6 类别 × 可配通道），缺项以默认值补齐。 */
    List<PreferenceItem> getForUser(long tenantId, long userId);

    /** 批量 upsert 偏好；STATION 通道的关闭请求被忽略（站内信强制开启）。 */
    void update(long tenantId, long userId, List<PreferenceItem> items);

    /**
     * 单条偏好。
     *
     * @param category 通知类别
     * @param channel  通道
     * @param enabled  是否接收
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class PreferenceItem {
        private NotificationCategory category;
        private Channel channel;
        private boolean enabled;

    }
}
