import type { HttpClient } from './http';
import type {
  TicketDetailView,
  TicketMessageView,
  TicketPage,
  TicketPriority,
  TicketStatus,
  TicketType,
  TicketSlaState,
  TicketAttachmentView,
} from './ticket';

/**
 * 运营端「工单管理」。对应 boot `/api/v1/ops/tickets/**`。
 *
 * 运营客服跨租户处理：列表/操作按运营 dataScope 收敛（OWNED 仅看 csm_user_id=self
 * 的租户）。权限点 ops.ticket.view（只读）/ ops.ticket.handle（认领/回复/转单/改优先级/
 * 解决/关闭）/ ops.ticket.reopen（强制重启，仅超管）。雪花 Long 全程字符串。
 */

/** 受理人选项（运营成员）。 */
export type TicketAssigneeOption = {
  userId: string;
  name: string | null;
};

/** 运营端工单列表查询（page 1-based，size≤200）。 */
export type OpsTicketQuery = {
  status?: TicketStatus | undefined;
  keyword?: string | undefined;
  tenantId?: string | undefined;
  type?: TicketType | undefined;
  priority?: TicketPriority | undefined;
  assigneeId?: string | undefined;
  slaState?: TicketSlaState | undefined;
  mineOnly?: boolean | undefined;
  page?: number | undefined;
  size?: number | undefined;
};

type RawPage = Omit<TicketPage, 'total'> & { total: string | number };

export function opsTicketApi(http: HttpClient) {
  const base = '/api/v1/ops/tickets';
  return {
    /** 工单列表（多条件筛选 + SLA 态 + 仅我的）。 */
    async list(query?: OpsTicketQuery): Promise<TicketPage> {
      const q = query ?? {};
      const params = {
        ...(q.status ? { status: q.status } : {}),
        ...(q.keyword ? { keyword: q.keyword } : {}),
        ...(q.tenantId ? { tenantId: q.tenantId } : {}),
        ...(q.type ? { type: q.type } : {}),
        ...(q.priority ? { priority: q.priority } : {}),
        ...(q.assigneeId ? { assigneeId: q.assigneeId } : {}),
        ...(q.slaState ? { slaState: q.slaState } : {}),
        ...(q.mineOnly ? { mineOnly: true } : {}),
        ...(q.page ? { page: q.page } : {}),
        ...(q.size ? { size: q.size } : {}),
      };
      const res = await http.get<RawPage>(base, { params });
      return { ...res.data, total: Number(res.data.total) };
    },
    /** 工单详情。 */
    async getDetail(ticketId: string): Promise<TicketDetailView> {
      const res = await http.get<TicketDetailView>(`${base}/${encodeURIComponent(ticketId)}`);
      return res.data;
    },
    /** 受理人下拉（启用的运营成员）。 */
    async assignees(): Promise<TicketAssigneeOption[]> {
      const res = await http.get<TicketAssigneeOption[]>(`${base}/assignees`);
      return res.data;
    },
    /** 认领（PENDING → PROCESSING，受理人 = 当前运营）。 */
    async claim(ticketId: string): Promise<void> {
      await http.post(`${base}/${encodeURIComponent(ticketId)}/claim`);
    },
    /** 转单给指定运营成员。 */
    async transfer(ticketId: string, assigneeId: string): Promise<void> {
      await http.post(`${base}/${encodeURIComponent(ticketId)}/transfer`, { assigneeId });
    },
    /** 调整优先级（同时重算 SLA 到期）。 */
    async changePriority(ticketId: string, priority: TicketPriority): Promise<void> {
      await http.put(`${base}/${encodeURIComponent(ticketId)}/priority`, { priority });
    },
    /** 客服回复。 */
    async reply(ticketId: string, content: string): Promise<TicketMessageView> {
      const res = await http.post<TicketMessageView>(
        `${base}/${encodeURIComponent(ticketId)}/reply`,
        { content },
      );
      return res.data;
    },
    /** 标记已解决（PROCESSING → RESOLVED）。 */
    async resolve(ticketId: string): Promise<void> {
      await http.post(`${base}/${encodeURIComponent(ticketId)}/resolve`);
    },
    /** 关闭工单。 */
    async close(ticketId: string, reason: string): Promise<void> {
      await http.post(`${base}/${encodeURIComponent(ticketId)}/close`, { reason });
    },
    /** 强制重启（CLOSED → PROCESSING，需 ops.ticket.reopen）。 */
    async reopen(ticketId: string): Promise<void> {
      await http.post(`${base}/${encodeURIComponent(ticketId)}/reopen`);
    },
    /** 上传附件（multipart）。 */
    async addAttachment(
      ticketId: string,
      file: File,
      messageId?: string,
    ): Promise<TicketAttachmentView> {
      const form = new FormData();
      form.append('file', file);
      const res = await http.post<TicketAttachmentView>(
        `${base}/${encodeURIComponent(ticketId)}/attachments`,
        form,
        { ...(messageId ? { params: { messageId } } : {}) },
      );
      return res.data;
    },
    /** 取附件签名下载地址。 */
    async attachmentUrl(ticketId: string, attachmentId: string): Promise<string> {
      const res = await http.get<{ url: string }>(
        `${base}/${encodeURIComponent(ticketId)}/attachments/${encodeURIComponent(attachmentId)}/url`,
      );
      return res.data.url;
    },
  };
}
