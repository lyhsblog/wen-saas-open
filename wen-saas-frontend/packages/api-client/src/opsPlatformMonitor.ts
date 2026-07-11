import type { HttpClient } from './http';

/**
 * 运营端「平台运维监控看板」。对应 boot `/api/v1/ops/platform/**`（7 endpoint）。
 *
 * 跨模块业务指标看板，全部实时聚合、按当前用户 dataScope 收敛、`ops.monitor.view` 权限门控。
 * 计数类为普通 long → number；雪花 Long（tenantId / webhookId）JSON 为字符串 → string。
 */

/** 顶部 4 KPI 卡 + 近 14 天 spark + 紧急到期计数。 */
export type OpsMonitorOverview = {
  activeTenants: number;
  activeTenantsDelta: number;
  dau: number;
  dauDeltaPct: number;
  apiCallsToday: number;
  apiCallsDeltaPct: number;
  openTickets: number;
  openTicketsDelta: number;
  tenantSpark: number[];
  dauSpark: number[];
  apiSpark: number[];
  ticketSpark: number[];
  expiringIn30Days: number;
  expiringIn7Days: number;
};

export type OpsMonitorTrendType = 'active' | 'tenant' | 'api' | 'ticket';
export type OpsMonitorRange = '7d' | '30d' | 'mtd' | 'qtr';

/** 趋势分析区双轴折线数据。 */
export type OpsMonitorTrend = {
  dates: string[];
  y1: number[];
  y2: number[];
  y1Label: string;
  y2Label: string;
};

/** 业务模块使用情况（真实注册模块，近 30 天）。 */
export type OpsMonitorModuleMetric = {
  code: string;
  name: string;
  enabledTenants: number;
  calls: number;
  dau: number;
  growthPct: number;
};

/** 套餐分布切片。 */
export type OpsMonitorPlanSlice = {
  packageCode: string;
  packageName: string;
  count: number;
};

/** 近 6 月续费率趋势 + 底部统计。 */
export type OpsMonitorRenewalTrend = {
  months: string[];
  rates: number[];
  expiringSoon: number;
  renewedThisMonth: number;
  addonThisMonth: number;
};

/** 资源使用 Top 租户项（tenantId 雪花 → string）。 */
export type OpsMonitorTenantSize = {
  tenantId: string;
  tenantName: string;
  size: number;
  /** 该租户是否已软删（展示真实名 + 「已删除」标注）。 */
  tenantDeleted: boolean;
};

/** 资源使用概览。 */
export type OpsMonitorResourceUsage = {
  totalFiles: number;
  totalSize: number;
  platformSize: number;
  tenantOssSize: number;
  topTenants: OpsMonitorTenantSize[];
  incrementThisPeriod: number;
};

/** Webhook 失败 Top（webhookId 雪花 → string）。 */
export type OpsMonitorWebhookFailure = {
  webhookId: string;
  /** 端点名（已删除端点为 null，前端回退 #webhookId）。 */
  webhookName: string | null;
  eventType: string;
  failCount: number;
  successRate: number;
};

/** API · Webhook 概览（近 7 天）。 */
export type OpsMonitorApiWebhook = {
  apiCalls7d: number;
  apiErrors7d: number;
  webhookPushes7d: number;
  webhookSuccessRate: number;
  topFailures: OpsMonitorWebhookFailure[];
};

export function opsPlatformMonitorApi(http: HttpClient) {
  const base = '/api/v1/ops/platform';
  return {
    async overview(): Promise<OpsMonitorOverview> {
      const res = await http.get<OpsMonitorOverview>(`${base}/overview`);
      return res.data;
    },
    async trends(type: OpsMonitorTrendType, range: OpsMonitorRange): Promise<OpsMonitorTrend> {
      const res = await http.get<OpsMonitorTrend>(`${base}/trends`, { params: { type, range } });
      return res.data;
    },
    async moduleMetrics(): Promise<OpsMonitorModuleMetric[]> {
      const res = await http.get<OpsMonitorModuleMetric[]>(`${base}/modules/metrics`);
      return res.data;
    },
    async planDistribution(): Promise<OpsMonitorPlanSlice[]> {
      const res = await http.get<OpsMonitorPlanSlice[]>(`${base}/plans/distribution`);
      // count 是后端 Long，按本仓约定序列化为字符串；此处转成 number，
      // 否则前端 reduce(s + count) 会变成字符串拼接（如 "0"+"2"+"2"="022"），
      // 环图占比、图例数值、中心总数全部错乱。
      return res.data.map((s) => ({ ...s, count: Number(s.count) }));
    },
    async renewalTrend(): Promise<OpsMonitorRenewalTrend> {
      const res = await http.get<OpsMonitorRenewalTrend>(`${base}/renewals/trend`);
      return res.data;
    },
    async resourceUsage(): Promise<OpsMonitorResourceUsage> {
      const res = await http.get<OpsMonitorResourceUsage>(`${base}/resources/usage`);
      return res.data;
    },
    async apiWebhookOverview(): Promise<OpsMonitorApiWebhook> {
      const res = await http.get<OpsMonitorApiWebhook>(`${base}/api-webhook/overview`);
      return res.data;
    },
  };
}
