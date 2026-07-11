package com.wensaas.core.api.notification.send;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 业务方发送通知请求。
 *
 * @param templateCode      模板编码 {@code {module}.{template_name}}
 * @param variables         变量替换值；未传的变量保留 {@code {var}} 占位符（不报错）
 * @param recipients        收件人规格列表（成员 / 角色 / 部门 / 部门负责人）
 * @param channels          显式指定通道；null/空 = 用模板 default_channels
 * @param noStationFallback true = 不强制兜底站内信（罕见，需业务方明确）；默认 false
 * @param category          站内信分类（仅用于 STATION 通道写入 sys_station_message.category）
 * @param relatedType       关联业务类型（如 "Customer"）
 * @param relatedId         关联业务 ID（字符串以兼容非雪花 ID）
 * @param link              点击跳转 URL（前端路由相对路径）
 * @param priority          优先级；null = 用模板 priority
 */
public record MessageSendRequest(
        String templateCode,
        Map<String, Object> variables,
        List<RecipientSpec> recipients,
        List<Channel> channels,
        boolean noStationFallback,
        MessageCategory category,
        String relatedType,
        String relatedId,
        String link,
        Priority priority) {

    public MessageSendRequest {
        Objects.requireNonNull(templateCode, "templateCode");
        Objects.requireNonNull(recipients, "recipients");
        if (templateCode.isBlank()) {
            throw new IllegalArgumentException("templateCode 不可空");
        }
        if (recipients.isEmpty()) {
            throw new IllegalArgumentException("收件人列表不可空");
        }
        if (variables == null) {
            variables = Map.of();
        }
        if (channels == null) {
            channels = List.of();
        }
        if (category == null) {
            category = MessageCategory.BUSINESS;
        }
    }

    public static Builder builder(String templateCode) {
        return new Builder(templateCode);
    }

    public static final class Builder {
        private final String templateCode;
        private Map<String, Object> variables = Map.of();
        private List<RecipientSpec> recipients = List.of();
        private List<Channel> channels = List.of();
        private boolean noStationFallback;
        private MessageCategory category = MessageCategory.BUSINESS;
        private String relatedType;
        private String relatedId;
        private String link;
        private Priority priority;

        private Builder(String templateCode) {
            this.templateCode = templateCode;
        }

        public Builder variables(Map<String, Object> v) { this.variables = v; return this; }
        public Builder recipients(List<RecipientSpec> r) { this.recipients = r; return this; }
        public Builder channels(List<Channel> c) { this.channels = c; return this; }
        public Builder noStationFallback(boolean v) { this.noStationFallback = v; return this; }
        public Builder category(MessageCategory c) { this.category = c; return this; }
        public Builder related(String type, String id) { this.relatedType = type; this.relatedId = id; return this; }
        public Builder link(String l) { this.link = l; return this; }
        public Builder priority(Priority p) { this.priority = p; return this; }

        public MessageSendRequest build() {
            return new MessageSendRequest(templateCode, variables, recipients, channels,
                    noStationFallback, category, relatedType, relatedId, link, priority);
        }
    }
}
