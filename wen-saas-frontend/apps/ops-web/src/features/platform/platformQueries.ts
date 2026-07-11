import { useQuery } from '@tanstack/react-query';
import {
  opsHealthApi,
  opsPlatformMonitorApi,
  type OpsMonitorRange,
  type OpsMonitorTrendType,
} from '@wen-saas/api-client';
import { http } from '../../lib/http';

const api = opsPlatformMonitorApi(http);
const healthApi = opsHealthApi(http);

export const platformKeys = {
  all: ['ops', 'platform'] as const,
  overview: () => [...platformKeys.all, 'overview'] as const,
  trends: (type: OpsMonitorTrendType, range: OpsMonitorRange) =>
    [...platformKeys.all, 'trends', type, range] as const,
  modules: () => [...platformKeys.all, 'modules'] as const,
  plans: () => [...platformKeys.all, 'plans'] as const,
  renewals: () => [...platformKeys.all, 'renewals'] as const,
  resources: () => [...platformKeys.all, 'resources'] as const,
  apiWebhook: () => [...platformKeys.all, 'api-webhook'] as const,
};

export function useMonitorOverview() {
  return useQuery({
    queryKey: platformKeys.overview(),
    queryFn: () => api.overview(),
  });
}

export function useMonitorTrends(type: OpsMonitorTrendType, range: OpsMonitorRange) {
  return useQuery({
    queryKey: platformKeys.trends(type, range),
    queryFn: () => api.trends(type, range),
  });
}

export function useMonitorModules() {
  return useQuery({
    queryKey: platformKeys.modules(),
    queryFn: () => api.moduleMetrics(),
  });
}

/** 首屏外区块懒加载用：enabled=false 时挂起取数，进入视口后再放行。 */
type LazyOptions = { enabled?: boolean };

export function useMonitorPlans(options?: LazyOptions) {
  return useQuery({
    queryKey: platformKeys.plans(),
    queryFn: () => api.planDistribution(),
    enabled: options?.enabled ?? true,
  });
}

export function useMonitorRenewals(options?: LazyOptions) {
  return useQuery({
    queryKey: platformKeys.renewals(),
    queryFn: () => api.renewalTrend(),
    enabled: options?.enabled ?? true,
  });
}

export function useMonitorResources(options?: LazyOptions) {
  return useQuery({
    queryKey: platformKeys.resources(),
    queryFn: () => api.resourceUsage(),
    enabled: options?.enabled ?? true,
  });
}

export function useMonitorApiWebhook(options?: LazyOptions) {
  return useQuery({
    queryKey: platformKeys.apiWebhook(),
    queryFn: () => api.apiWebhookOverview(),
    enabled: options?.enabled ?? true,
  });
}

/** 健康度三色概览，复用运营客户健康度端点（看板「客户健康度概览」卡）。 */
export function useMonitorHealth(options?: LazyOptions) {
  return useQuery({
    queryKey: [...platformKeys.all, 'health'] as const,
    queryFn: () => healthApi.overview(),
    enabled: options?.enabled ?? true,
  });
}
