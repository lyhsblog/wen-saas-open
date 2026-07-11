import type { HttpClient } from './http';

/**
 * 工单系统。租户侧 `/api/v1/base/tickets/**`、运营侧
 * `/api/v1/ops/tickets/**`（见 opsTicket.ts）。本文件放两端共享的视图类型 +
 * 租户侧工厂 `tenantTicketApi`。
 *
 * 雪花 Long（id / tenantId / submitterId / assigneeId / messageId / total /
 * sizeBytes）JSON 序列化为字符串，前端类型用 string，URL / queryKey 不转 number
 * 防精度丢失；total / sizeBytes 仅在展示/分页计算处局部 Number()。枚举（type /
 * priority / status / authorSide）按 NAME 传参与回包。
 */

/** 工单类型。 */
export type TicketType = 'BUG' | 'CONSULT' | 'SUGGESTION' | 'COMPLAINT';

/** 工单优先级（决定 SLA 时限）。 */
export type TicketPriority = 'LOW' | 'NORMAL' | 'HIGH' | 'URGENT';

/** 工单状态。 */
export type TicketStatus = 'PENDING' | 'PROCESSING' | 'RESOLVED' | 'CLOSED';

/** 留言作者立场。 */
export type TicketAuthorSide = 'TENANT' | 'OPS';

/** SLA 实时态：normal 正常 / warning 临近 / breach 已超时。 */
export type TicketSlaState = 'normal' | 'warning' | 'breach';

/** 工单附件视图。messageId 为 null 表示挂在工单本身而非某条留言。 */
export type TicketAttachmentView = {
  id: string;
  messageId: string | null;
  fileId: string;
  fileName: string;
  /** 字节数（后端 long → 字符串），展示前 Number()。 */
  sizeBytes: string;
  createdAt: string;
};

/** 工单留言视图。 */
export type TicketMessageView = {
  id: string;
  authorId: string | null;
  authorName: string | null;
  authorSide: TicketAuthorSide;
  content: string;
  createdAt: string;
  attachments: TicketAttachmentView[];
};

/** 工单摘要（列表项 / 详情头）。 */
export type TicketSummary = {
  id: string;
  ticketNo: string;
  title: string;
  type: TicketType;
  priority: TicketPriority;
  status: TicketStatus;
  tenantId: string | null;
  tenantName: string | null;
  submitterId: string | null;
  submitterName: string | null;
  assigneeId: string | null;
  assigneeName: string | null;
  slaDueAt: string | null;
  slaState: TicketSlaState;
  lastActivityAt: string | null;
  createdAt: string | null;
};

/** 工单详情。 */
export type TicketDetailView = {
  summary: TicketSummary;
  resolvedAt: string | null;
  closedAt: string | null;
  closeReason: string | null;
  messages: TicketMessageView[];
  attachments: TicketAttachmentView[];
};

/** 分页结果。total 后端为 long（字符串），api 层转回 number 供分页计算。 */
export type TicketPage = {
  records: TicketSummary[];
  total: number;
  page: number;
  size: number;
};

/** 提交工单请求。 */
export type SubmitTicketRequest = {
  title: string;
  type: TicketType;
  priority: TicketPriority;
  content: string;
};

/** 我的工单列表查询（page 1-based，size≤200）。 */
export type MyTicketsQuery = {
  status?: TicketStatus | undefined;
  page?: number | undefined;
  size?: number | undefined;
};

/** 全部工单（租户内，需 base.ticket.view_all）查询。 */
export type TenantAllTicketsQuery = {
  status?: TicketStatus | undefined;
  keyword?: string | undefined;
  page?: number | undefined;
  size?: number | undefined;
};

type RawPage = Omit<TicketPage, 'total'> & { total: string | number };

function normalizePage(raw: RawPage): TicketPage {
  return { ...raw, total: Number(raw.total) };
}

export function tenantTicketApi(http: HttpClient) {
  const base = '/api/v1/base/tickets';
  return {
    /** 提交工单（提交人 = 当前登录成员）。 */
    async submit(body: SubmitTicketRequest): Promise<TicketDetailView> {
      const res = await http.post<TicketDetailView>(base, body);
      return res.data;
    },
    /** 我提交的工单（可按状态过滤）。 */
    async myTickets(query?: MyTicketsQuery): Promise<TicketPage> {
      const params = {
        ...(query?.status ? { status: query.status } : {}),
        ...(query?.page ? { page: query.page } : {}),
        ...(query?.size ? { size: query.size } : {}),
      };
      const res = await http.get<RawPage>(base, { params });
      return normalizePage(res.data);
    },
    /** 本租户全部工单（需 base.ticket.view_all 权限）。 */
    async allTickets(query?: TenantAllTicketsQuery): Promise<TicketPage> {
      const params = {
        ...(query?.status ? { status: query.status } : {}),
        ...(query?.keyword ? { keyword: query.keyword } : {}),
        ...(query?.page ? { page: query.page } : {}),
        ...(query?.size ? { size: query.size } : {}),
      };
      const res = await http.get<RawPage>(`${base}/all`, { params });
      return normalizePage(res.data);
    },
    /** 工单详情。 */
    async getDetail(ticketId: string): Promise<TicketDetailView> {
      const res = await http.get<TicketDetailView>(`${base}/${encodeURIComponent(ticketId)}`);
      return res.data;
    },
    /** 追加留言。 */
    async reply(ticketId: string, content: string): Promise<TicketMessageView> {
      const res = await http.post<TicketMessageView>(
        `${base}/${encodeURIComponent(ticketId)}/reply`,
        { content },
      );
      return res.data;
    },
    /** 确认已解决（RESOLVED → CLOSED）。 */
    async confirm(ticketId: string): Promise<void> {
      await http.post(`${base}/${encodeURIComponent(ticketId)}/confirm`);
    },
    /** 重启工单（RESOLVED / 7 天内 CLOSED → PROCESSING）。 */
    async reopen(ticketId: string): Promise<void> {
      await http.post(`${base}/${encodeURIComponent(ticketId)}/reopen`);
    },
    /** 上传附件（multipart）。messageId 不传则挂在工单本身。 */
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
