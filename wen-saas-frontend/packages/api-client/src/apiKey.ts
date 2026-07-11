import type { HttpClient } from './http';

export interface ApiKeyView {
  id: string;
  name: string;
  /** 公开标识（ak_ 前缀），非敏感；secret 不在任何查询响应中出现。 */
  apiKey: string;
  /** active / disabled */
  status: string;
  requireSignature: boolean;
  ipWhitelist: string[];
  /** 凭证级限流（每窗口请求数）；null=仅受租户级限流。 */
  rateLimitQps: number | null;
  expiresAt: string | null;
  lastUsedAt: string | null;
  createdAt: string;
}

/** 签发 / 轮换返回：secret 为明文，仅此一次，需提示用户立即保存。 */
export interface ApiKeyCreated {
  id: string;
  name: string;
  apiKey: string;
  secret: string;
}

export interface ApiKeyUpsertRequest {
  name: string;
  expiresAt?: string;
  ipWhitelist?: string[];
  rateLimitQps?: number;
  requireSignature?: boolean;
}

/** 调用日志行（apiKeyName 行内不冗余，按凭证列表映射名称）。 */
export interface ApiCallLogItem {
  id: string;
  apiKeyId: string | null;
  requestId: string;
  method: string;
  path: string;
  statusCode: number | null;
  durationMs: number | null;
  errorMessage: string | null;
  ip: string | null;
  moduleCode: string | null;
  createdAt: string;
}

export interface ApiCallLogPage {
  records: ApiCallLogItem[];
  total: number;
}

export interface ApiCallLogQuery {
  apiKeyId?: string;
  statusCode?: number;
  /** 本地时间 ISO（yyyy-MM-ddTHH:mm:ss），含。 */
  from?: string;
  /** 本地时间 ISO，不含。 */
  to?: string;
  page?: number;
  size?: number;
}

/** 租户后台 · 开放 API 凭证管理 API。 */
export function tenantApiKeyApi(http: HttpClient) {
  const base = '/api/v1/base/api-keys';
  return {
    async list(): Promise<ApiKeyView[]> {
      const res = await http.get<ApiKeyView[]>(base);
      return res.data;
    },
    async create(body: ApiKeyUpsertRequest): Promise<ApiKeyCreated> {
      const res = await http.post<ApiKeyCreated>(base, body);
      return res.data;
    },
    async update(id: string, body: ApiKeyUpsertRequest): Promise<ApiKeyView> {
      const res = await http.put<ApiKeyView>(`${base}/${encodeURIComponent(id)}`, body);
      return res.data;
    },
    async enable(id: string): Promise<void> {
      await http.post(`${base}/${encodeURIComponent(id)}/enable`, null);
    },
    async disable(id: string): Promise<void> {
      await http.post(`${base}/${encodeURIComponent(id)}/disable`, null);
    },
    async rotateSecret(id: string): Promise<ApiKeyCreated> {
      const res = await http.post<ApiKeyCreated>(
        `${base}/${encodeURIComponent(id)}/rotate-secret`,
        null,
      );
      return res.data;
    },
    async callLogs(params: ApiCallLogQuery): Promise<ApiCallLogPage> {
      const res = await http.get<ApiCallLogPage>(`${base}/call-logs`, { params });
      return res.data;
    },
  };
}
