import type { HttpClient } from './http';

/**
 * 运营端「工作台首页」。对应 boot `/api/v1/ops/dashboard/**`（3 endpoint）。
 *
 * 指标 + 待办 + 近期动态均按当前用户 dataScope 过滤：OPS_OWNED 时只看自己归属
 * （csm_user_id=self）的租户对应数据。雪花 Long（id / operatorId / targetTenantId）
 * JSON 为字符串，类型用 string；OverviewMetrics 的计数为普通 long 数值，用 number。
 */

/** 4 张关键指标卡片 + 同比 + 紧急计数。 */
export type OpsDashboardOverview = {
  /** 在租租户数（status=1 且未软删）。 */
  activeTenants: number;
  /** 与上月末同口径相比的差值（正/负/0）。 */
  activeTenantsDeltaVsLastMonth: number;
  /** 本月新增租户数。 */
  newTenantsThisMonth: number;
  /** 本月续费操作次数。 */
  renewalsThisMonth: number;
  /** 30 天内到期租户数。 */
  expiringIn30Days: number;
  /** 紧急到期（7 天内）。 */
  expiringIn7Days: number;
};

/** 待办严重度。 */
export type OpsTodoSeverity = 'urgent' | 'warn' | 'info';

/** 今日待办项。 */
export type OpsTodoItem = {
  /** tenant_expiring / password_reset_pending / ticket_pending */
  type: string;
  title: string;
  summary: string | null;
  severity: OpsTodoSeverity;
  targetTenantId: string | null;
  targetTenantName: string | null;
  actionUrl: string | null;
  dueAt: string | null;
};

/** 近期动态（运营操作流）。 */
export type OpsRecentActivity = {
  id: string;
  operatorId: string;
  operatorName: string | null;
  action: string;
  objectDesc: string | null;
  targetTenantId: string | null;
  targetTenantName: string | null;
  createdAt: string | null;
};

export function opsDashboardApi(http: HttpClient) {
  const base = '/api/v1/ops/dashboard';
  return {
    /** 4 张关键指标卡片 + 同比 + 紧急计数。 */
    async overview(): Promise<OpsDashboardOverview> {
      const res = await http.get<OpsDashboardOverview>(`${base}/overview`);
      return res.data;
    },
    /** 今日待办（到期租户 + 重置密码请求 + 工单占位）。 */
    async todos(): Promise<OpsTodoItem[]> {
      const res = await http.get<OpsTodoItem[]>(`${base}/todos`);
      return res.data;
    },
    /** 近期动态（运营操作日志倒序前 N 条）。 */
    async recentActivities(limit?: number): Promise<OpsRecentActivity[]> {
      const params = { ...(limit != null ? { limit } : {}) };
      const res = await http.get<OpsRecentActivity[]>(`${base}/recent-activities`, { params });
      return res.data;
    },
  };
}
