package com.wensaas.core.api.notification.inbox;

/** 单分类未读计数。 */
public record UnreadCount(String category, long count) {
}
