package com.wensaas.core.api.notification.send;

import java.util.Objects;

/**
 * 业务方调 {@link MessageService#send} 后拿到的任务 ID。
 * 同一次 send 拆分出的多条 sys_message_task 共享同一个 taskId，便于聚合查询状态。
 */
public record MessageTaskId(String value) {

    public MessageTaskId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("taskId 不可空");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
