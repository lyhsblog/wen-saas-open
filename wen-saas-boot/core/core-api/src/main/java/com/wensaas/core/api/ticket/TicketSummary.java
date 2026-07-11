package com.wensaas.core.api.ticket;

import java.time.Instant;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * 工单列表项 / 详情头。雪花 id（Long）由 JSON 层序列化为字符串，前端用 string。
 *
 * @param slaState 衍生 SLA 状态：normal 正常 / warning 临近 / breach 超时
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public class TicketSummary {
    private Long id;
    private String ticketNo;
    private String title;
    private TicketType type;
    private TicketPriority priority;
    private TicketStatus status;
    private Long tenantId;
    private String tenantName;
    private Long submitterId;
    private String submitterName;
    private Long assigneeId;
    private String assigneeName;
    private Instant slaDueAt;
    private String slaState;
    private Instant lastActivityAt;
    private Instant createdAt;

}
