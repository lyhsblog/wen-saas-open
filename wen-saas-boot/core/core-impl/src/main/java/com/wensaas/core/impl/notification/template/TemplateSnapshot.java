package com.wensaas.core.impl.notification.template;

import com.wensaas.core.api.notification.send.Channel;
import com.wensaas.core.api.notification.send.Priority;
import com.wensaas.core.api.notification.template.TemplateContent;

import java.util.List;
import java.util.Map;

/**
 * 模板解析后的轻量快照，参与 Redis 缓存。
 *
 * <p>从 {@link SysNotificationTemplate} 转换而来；只持有发送链路真正用得到的字段，
 * 避免缓存 entity 引入 MP 注解/时间字段等额外噪声。
 *
 * @param tenantId         实际命中的 tenant_id（0 = 业务方默认；>0 = 租户定制）
 * @param templateCode     模板编码
 * @param moduleCode       来源模块（可空）
 * @param defaultChannels  默认通道列表
 * @param contents         各通道文案
 * @param priority         优先级
 * @param notificationCategory 通知类别（{@code NotificationCategory} 枚举名，可空）；
 *                             非空时投递参与用户通知偏好过滤
 * @param suppressed       租户主管理员已停用该模板——
 *                             {@code true} 时发送链路静默跳过、不投递、不抛错（含站内信兜底）。
 *                             其余字段在 suppressed 场景下仅作占位
 */
public record TemplateSnapshot(
        long tenantId,
        String templateCode,
        String moduleCode,
        List<Channel> defaultChannels,
        Map<Channel, TemplateContent> contents,
        Priority priority,
        String notificationCategory,
        boolean suppressed) {

    public TemplateSnapshot {
        if (defaultChannels == null) {
            defaultChannels = List.of();
        }
        if (contents == null) {
            contents = Map.of();
        }
        if (priority == null) {
            priority = Priority.NORMAL;
        }
    }

    /** 便捷构造：非停用快照（{@code suppressed=false}）。 */
    public TemplateSnapshot(long tenantId, String templateCode, String moduleCode,
                            List<Channel> defaultChannels, Map<Channel, TemplateContent> contents,
                            Priority priority, String notificationCategory) {
        this(tenantId, templateCode, moduleCode, defaultChannels, contents, priority,
                notificationCategory, false);
    }
}
