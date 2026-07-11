package com.wensaas.core.api.notification.send;

/** 消息优先级（NORMAL 普通 / IMPORTANT 重要）：模板带默认值，租户偏好与单次发送可覆盖。 */
public enum Priority {
    NORMAL,
    IMPORTANT
}
