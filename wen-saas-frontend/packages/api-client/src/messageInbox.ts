import type { HttpClient } from './http';

export type MessageCategory = 'SYSTEM' | 'BUSINESS' | 'TODO';
export type MessageReadState = 'ALL' | 'READ' | 'UNREAD';

export type InboxItem = {
  /** 雪花 id，序列化为字符串。 */
  id: string;
  category: string;
  sourceModule?: string | null;
  title: string;
  summary?: string | null;
  priority: string;
  read: boolean;
  createdAt: string;
};

export type InboxPage = {
  records: InboxItem[];
  total: number;
  page: number;
  size: number;
};

export type MessageDetailView = {
  id: string;
  category: string;
  sourceModule?: string | null;
  title: string;
  /** 关联对象无权访问时降级为占位文案。 */
  body?: string | null;
  relatedType?: string | null;
  relatedId?: string | null;
  /** 关联对象无权访问时为 null。 */
  link?: string | null;
  priority: string;
  read: boolean;
  createdAt: string;
};

export type UnreadCount = { category: string; count: number };
export type UnreadSummary = { total: number; byCategory: UnreadCount[] };

export type DeliveryDetail = {
  channel: string;
  status: string;
  sentAt?: string | null;
  retryCount: number;
  errorReason?: string | null;
};

export type InboxQuery = {
  category?: MessageCategory | null;
  readState?: MessageReadState;
  keyword?: string;
  archived?: boolean;
  page?: number;
  size?: number;
};

/** 站内信收件箱 API（/api/v1/messages/inbox/**）。 */
export function messageInboxApi(http: HttpClient) {
  const base = '/api/v1/messages/inbox';
  return {
    async list(query: InboxQuery = {}): Promise<InboxPage> {
      const res = await http.get<InboxPage>(base, {
        params: {
          ...(query.category ? { category: query.category } : {}),
          readState: query.readState ?? 'ALL',
          ...(query.keyword ? { keyword: query.keyword } : {}),
          archived: query.archived ?? false,
          page: query.page ?? 1,
          size: query.size ?? 20,
        },
      });
      return res.data;
    },
    async detail(id: string): Promise<MessageDetailView> {
      const res = await http.get<MessageDetailView>(`${base}/${id}`);
      return res.data;
    },
    async markRead(id: string): Promise<void> {
      await http.post(`${base}/${id}/read`);
    },
    async markAllRead(category?: MessageCategory | null): Promise<void> {
      await http.post(`${base}/read-all`, null, {
        params: category ? { category } : {},
      });
    },
    async remove(id: string): Promise<void> {
      await http.delete(`${base}/${id}`);
    },
    async unreadCount(): Promise<UnreadSummary> {
      const res = await http.get<UnreadSummary>(`${base}/unread-count`);
      return res.data;
    },
    async deliveries(id: string): Promise<DeliveryDetail[]> {
      const res = await http.get<DeliveryDetail[]>(`${base}/${id}/deliveries`);
      return res.data;
    },
  };
}
