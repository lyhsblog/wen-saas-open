import type { HttpClient } from './http';

/**
 * 运营端「客户健康度」。对应 boot `/api/v1/ops/health/**`（3 endpoint）。
 *
 * 纯信息看板：总览三档计数 + 租户健康度列表 + 单租户详情。评分每天凌晨由后端 Job 计算，
 * 列表/总览读快照，详情实时重算风险信号。dataScope=OWNED 的角色仅看 csm_user_id=self 的租户。
 * 雪花 Long（tenantId / total）JSON 为字符串。
 */

/** 健康度分档。 */
export type HealthLevel = 'healthy' | 'warning' | 'danger';

/** 命中的单个风险信号。weight 为正数扣分幅度。 */
export type RiskSignal = {
  code: string;
  label: string;
  weight: number;
};

/** 最近 30 天指标快照（无数据源的维度为 null）。 */
export type HealthMetrics = {
  monthlyActiveMembers: number | null;
  totalMembers: number | null;
  moduleAccessCount: number | null;
  /** 模块访问环比百分比（正/负），上一周期为 0 时 null。 */
  moduleAccessChangePct: number | null;
  dataGrowth: number | null;
  ticketCount: number | null;
};

/** 总览三档卡片 + 环比增减（当前档 - 上次同档数量，可正可负）。 */
export type HealthOverview = {
  healthy: number;
  healthyDelta: number;
  warning: number;
  warningDelta: number;
  danger: number;
  dangerDelta: number;
};

/** 列表项（读快照）。 */
export type HealthListItem = {
  tenantId: string;
  tenantName: string | null;
  tenantNo: string | null;
  score: number;
  level: HealthLevel;
  riskSignals: RiskSignal[];
  computedAt: string | null;
};

/** 分页结果。total 后端为 long（序列化为字符串），api 层转回 number 供分页计算。 */
export type HealthListPage = {
  records: HealthListItem[];
  total: number;
};

/** 详情（实时重算）。 */
export type HealthDetail = {
  tenantId: string;
  tenantName: string | null;
  tenantNo: string | null;
  score: number;
  level: HealthLevel;
  riskSignals: RiskSignal[];
  metrics: HealthMetrics;
  expireAt: string | null;
  computedAt: string | null;
};

/** 列表查询条件（page 1-based，size≤200）。 */
export type ListHealthQuery = {
  level?: HealthLevel | undefined;
  keyword?: string | undefined;
  page?: number | undefined;
  size?: number | undefined;
};

export function opsHealthApi(http: HttpClient) {
  const base = '/api/v1/ops/health';
  return {
    /** 总览三档计数 + 环比。 */
    async overview(): Promise<HealthOverview> {
      const res = await http.get<HealthOverview>(`${base}/overview`);
      return res.data;
    },
    /** 健康度列表（可按分档过滤 + 关键词搜租户名/编号）。 */
    async listTenants(query?: ListHealthQuery): Promise<HealthListPage> {
      const params = {
        ...(query?.level ? { level: query.level } : {}),
        ...(query?.keyword ? { keyword: query.keyword } : {}),
        ...(query?.page ? { page: query.page } : {}),
        ...(query?.size ? { size: query.size } : {}),
      };
      const res = await http.get<HealthListPage>(`${base}/tenants`, { params });
      return { ...res.data, total: Number(res.data.total) };
    },
    /** 单租户健康度详情（实时重算）。 */
    async getDetail(tenantId: string): Promise<HealthDetail> {
      const res = await http.get<HealthDetail>(`${base}/tenants/${encodeURIComponent(tenantId)}`);
      return res.data;
    },
  };
}
