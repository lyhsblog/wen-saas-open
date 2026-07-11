import type { HttpClient } from './http';

export interface WebhookView {
  id: string;
  name: string;
  targetUrl: string;
  subscribedEvents: string[];
  /** active / disabled */
  status: string;
  consecutiveFailures: number;
  createdAt: string;
  updatedAt: string;
}

/** 创建 / 轮换密钥的返回：signingSecret 为明文，仅此一次，需提示用户立即保存。 */
export interface WebhookCreated {
  id: string;
  name: string;
  signingSecret: string;
}

export interface WebhookUpsertRequest {
  name: string;
  targetUrl: string;
  subscribedEvents: string[];
}

export interface WebhookLogItem {
  id: string;
  eventType: string;
  eventId: string;
  httpStatus: number | null;
  durationMs: number | null;
  retryCount: number;
  /** success / failed(将重试) / abandoned(重试穷尽) */
  finalStatus: string;
  errorMessage: string | null;
  responseBody: string | null;
  createdAt: string;
}

export interface WebhookLogPage {
  records: WebhookLogItem[];
  /** 雪花/长整型在 JSON 中序列化为字符串。 */
  total: string;
}

/** 租户后台 · Webhook 订阅管理 API。 */
export function tenantWebhookApi(http: HttpClient) {
  const base = '/api/v1/base/webhooks';
  return {
    async list(): Promise<WebhookView[]> {
      const res = await http.get<WebhookView[]>(base);
      return res.data;
    },
    async create(body: WebhookUpsertRequest): Promise<WebhookCreated> {
      const res = await http.post<WebhookCreated>(base, body);
      return res.data;
    },
    async update(id: string, body: WebhookUpsertRequest): Promise<WebhookView> {
      const res = await http.put<WebhookView>(`${base}/${encodeURIComponent(id)}`, body);
      return res.data;
    },
    async enable(id: string): Promise<void> {
      await http.post(`${base}/${encodeURIComponent(id)}/enable`, null);
    },
    async disable(id: string): Promise<void> {
      await http.post(`${base}/${encodeURIComponent(id)}/disable`, null);
    },
    async remove(id: string): Promise<void> {
      await http.delete(`${base}/${encodeURIComponent(id)}`);
    },
    async rotateSecret(id: string): Promise<WebhookCreated> {
      const res = await http.post<WebhookCreated>(`${base}/${encodeURIComponent(id)}/rotate-secret`, null);
      return res.data;
    },
    async sendTest(id: string): Promise<{ eventId: string }> {
      const res = await http.post<{ eventId: string }>(`${base}/${encodeURIComponent(id)}/test`, null);
      return res.data;
    },
    async logs(
      id: string,
      params: { page?: number; size?: number; finalStatus?: string },
    ): Promise<WebhookLogPage> {
      const res = await http.get<WebhookLogPage>(`${base}/${encodeURIComponent(id)}/logs`, {
        params,
      });
      return res.data;
    },
  };
}
