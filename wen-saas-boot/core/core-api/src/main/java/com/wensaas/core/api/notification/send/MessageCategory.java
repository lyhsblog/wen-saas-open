package com.wensaas.core.api.notification.send;

/** 站内信分类（系统 / 业务 / 待办）：写入 {@code sys_station_message.category}，供消息中心按 Tab 筛选。 */
public enum MessageCategory {
    SYSTEM,
    BUSINESS,
    TODO
}
