package com.wensaas.core.api.ticket;

import java.io.InputStream;

/**
 * 租户侧工单服务（运行在租户上下文）。
 *
 * <p>提交 / 查看自己的工单：所有租户成员；查看本租户全部工单：需 {@code base.ticket.view_all}
 * （默认无，可在角色权限中赋权）。
 */
public interface TicketService {

    /** 提交工单（自动生成工单号 + SLA 截止；首条内容入沟通流；通知客服）。 */
    TicketDetailView submit(SubmitCommand cmd);

    /** 我提交的工单分页（可按状态过滤）。 */
    TicketPage myTickets(TicketStatus statusFilter, int page, int size);

    /** 本租户全部工单分页（需 {@code base.ticket.view_all}，由 Web 层守卫）。 */
    TicketPage tenantTickets(TicketStatus statusFilter, String keyword, int page, int size);

    /** 工单详情（提交人本人可见；他人需 {@code base.ticket.view_all}）。 */
    TicketDetailView detail(long ticketId);

    /**
     * 提交人追加回复（纯文本）。
     *
     * <p>若工单处于已解决/已关闭（7 天内），追加回复视为"仍未解决"重启 → 处理中并通知处理人。
     */
    TicketMessageView reply(long ticketId, String content);

    /** 提交人确认"问题已解决，关闭工单" → 已关闭。 */
    void confirmResolved(long ticketId);

    /** 提交人重新打开（已解决 或 已关闭 7 天内）→ 处理中并通知处理人。 */
    void reopen(long ticketId);

    /** 上传附件并关联到工单（可选挂到某条沟通消息）。visibility=business-only。 */
    TicketAttachmentView addAttachment(long ticketId, Long messageId,
                                       String fileName, String mimeType, long sizeBytes, InputStream content);

    /** 生成附件下载签名 URL（5 分钟有效）。 */
    String attachmentDownloadUrl(long ticketId, long attachmentId);

    /** 提交工单命令。 */
    record SubmitCommand(String title, TicketType type, TicketPriority priority, String content) {}
}
