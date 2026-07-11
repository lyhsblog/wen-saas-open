package com.wensaas.core.api.notification.inbox;

import com.wensaas.core.api.notification.access.RelatedObjectBody;
import com.wensaas.core.api.notification.access.RelatedObjectGuarded;
import com.wensaas.core.api.notification.access.RelatedObjectId;
import com.wensaas.core.api.notification.access.RelatedObjectLink;
import com.wensaas.core.api.notification.access.RelatedObjectType;

import java.time.LocalDateTime;

/**
 * 站内信详情视图。
 *
 * <p><b>必须是 class（非 record）+ 无参构造 + 字段可写</b>：{@code RelatedObjectGuardSerializer} 在
 * 序列化阶段对当前用户做关联对象权限探针，无权时生成降级副本（body 换占位、link 置 null）。
 */
@RelatedObjectGuarded(maskedBodyText = "该消息关联的内容已不可见")
public class MessageDetailView {

    private Long id;
    private String category;
    private String sourceModule;
    private String title;
    @RelatedObjectBody
    private String body;
    @RelatedObjectType
    private String relatedType;
    @RelatedObjectId
    private String relatedId;
    @RelatedObjectLink
    private String link;
    private String priority;
    private boolean read;
    private LocalDateTime createdAt;

    public MessageDetailView() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getSourceModule() { return sourceModule; }
    public void setSourceModule(String sourceModule) { this.sourceModule = sourceModule; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public String getRelatedType() { return relatedType; }
    public void setRelatedType(String relatedType) { this.relatedType = relatedType; }
    public String getRelatedId() { return relatedId; }
    public void setRelatedId(String relatedId) { this.relatedId = relatedId; }
    public String getLink() { return link; }
    public void setLink(String link) { this.link = link; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
