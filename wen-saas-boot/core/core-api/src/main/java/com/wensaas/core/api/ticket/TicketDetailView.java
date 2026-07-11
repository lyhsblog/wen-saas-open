package com.wensaas.core.api.ticket;

import java.time.Instant;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

/** 工单详情：头信息 + 沟通流 + 未挂在消息上的工单级附件。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public class TicketDetailView {
    private TicketSummary summary;
    private Instant resolvedAt;
    private Instant closedAt;
    private String closeReason;
    private List<TicketMessageView> messages;
    private List<TicketAttachmentView> attachments;

}
