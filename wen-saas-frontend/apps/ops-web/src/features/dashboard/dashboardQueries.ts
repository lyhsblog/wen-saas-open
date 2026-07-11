import { useQuery } from '@tanstack/react-query';
import { opsDashboardApi } from '@wen-saas/api-client';
import { http } from '../../lib/http';

const api = opsDashboardApi(http);

export const dashboardKeys = {
  all: ['ops', 'dashboard'] as const,
  overview: () => [...dashboardKeys.all, 'overview'] as const,
  todos: () => [...dashboardKeys.all, 'todos'] as const,
  recentActivities: (limit: number) => [...dashboardKeys.all, 'recent', limit] as const,
};

export function useOverview() {
  return useQuery({
    queryKey: dashboardKeys.overview(),
    queryFn: () => api.overview(),
  });
}

export function useTodos() {
  return useQuery({
    queryKey: dashboardKeys.todos(),
    queryFn: () => api.todos(),
  });
}

export function useRecentActivities(limit = 20) {
  return useQuery({
    queryKey: dashboardKeys.recentActivities(limit),
    queryFn: () => api.recentActivities(limit),
  });
}
