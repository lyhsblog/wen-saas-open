package com.wensaas.core.api.ticket;

import java.io.InputStream;
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

/**
 * 运营侧工单服务（运营客服，跨租户处理）。
 *
 * <p>读/写跨租户用 {@code @CrossTenantQuery}（impl 内开启忽略策略），写子表显式带工单 tenant_id。
 * 列表按当前运营 dataScope 过滤（OWNED 仅 {@code sys_tenant.csm_user_id=self} 的租户）。
 */
public interface OpsTicketService {

    /** 工单管理列表（多条件筛选 + SLA 状态 + 我的工单）。 */
    TicketPage list(OpsTicketQuery query);

    /** 工单详情（跨租户）。 */
    TicketDetailView detail(long ticketId);

    /** 认领：待处理 → 处理中，处理人=自己，通知提交人。 */
    void claim(long ticketId);

    /** 转单给其他客服。 */
    void transfer(long ticketId, long toAssigneeId);

    /** 调整优先级（按新优先级从创建时间重算 SLA 截止，重置临近/超时标记）。 */
    void changePriority(long ticketId, TicketPriority priority);

    /** 客服回复（纯文本）：通知提交人；若工单待处理则自动认领转处理中。 */
    TicketMessageView reply(long ticketId, String content);

    /** 标记已解决：处理中 → 已解决，记录解决时刻，通知提交人确认。 */
    void resolve(long ticketId);

    /** 关闭工单（填写关闭原因）→ 已关闭。 */
    void close(long ticketId, String reason);

    /** 强制重启已关闭工单（仅超管，{@code ops.ticket.reopen}）→ 处理中。 */
    void reopen(long ticketId);

    /** 上传附件并关联工单（运营侧，business-only）。 */
    TicketAttachmentView addAttachment(long ticketId, Long messageId,
                                       String fileName, String mimeType, long sizeBytes, InputStream content);

    /** 生成附件下载签名 URL。 */
    String attachmentDownloadUrl(long ticketId, long attachmentId);

    // ===================== 定时任务委托（由 Job 调用，无运营上下文） =====================

    /** SLA 扫描：临近(80%)发提醒标 warned、超时发提醒标 breached。返回处理计数。 */
    SlaScanResult runSlaScan(Instant now);

    /** 已解决满 7 天未回应的工单自动关闭并通知提交人。返回关闭条数。 */
    int runAutoClose(Instant now);

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class SlaScanResult {
        private int warned;
        private int breached;
    }

    /**
     * 工单管理筛选条件。空字段表示不限。
     *
     * @param slaState SLA 状态：normal 正常 / warning 临近 / breach 超时；null 不限
     * @param mineOnly 仅看处理人=当前客服
     */
    record OpsTicketQuery(
            TicketStatus status,
            String keyword,
            Long tenantId,
            TicketType type,
            TicketPriority priority,
            Long assigneeId,
            String slaState,
            boolean mineOnly,
            int page,
            int size) {
    }

    /** 可分配客服项（转单候选）。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class AssigneeOption {
        private Long userId;
        private String name;
    }

    /** 候选客服列表（运营账号）。 */
    List<AssigneeOption> assigneeOptions();
}
