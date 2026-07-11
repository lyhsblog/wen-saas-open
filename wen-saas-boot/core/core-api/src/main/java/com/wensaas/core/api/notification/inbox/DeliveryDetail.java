package com.wensaas.core.api.notification.inbox;

import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * 投递明细（单通道）。来自 sys_message_task；存量无 task_id 的历史消息兜底一条 STATION/SENT。
 *
 * @param channel      STATION / EMAIL / SMS / WECHAT / WECOM / DINGTALK
 * @param status       PENDING / SENT / FAILED / SKIPPED
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public class DeliveryDetail {
    private String channel;
    private String status;
    private LocalDateTime sentAt;
    private int retryCount;
    private String errorReason;

}
