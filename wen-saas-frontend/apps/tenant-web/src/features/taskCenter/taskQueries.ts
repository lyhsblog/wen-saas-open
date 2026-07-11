import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { TaskListQuery } from '@wen-saas/api-client';
import { taskApi } from './taskApi';

export const taskKeys = {
  all: ['tasks'] as const,
  list: (q: TaskListQuery) => [...taskKeys.all, 'list', q] as const,
  detail: (id: string) => [...taskKeys.all, 'detail', id] as const,
};

/** 单任务详情。enabled 时每 1s 轮询，进入终态自动停（导入向导执行步用）。 */
export function useTaskDetail(id: string | null, enabled: boolean) {
  return useQuery({
    queryKey: taskKeys.detail(id ?? '__none__'),
    queryFn: () => taskApi.get(id!),
    enabled: enabled && !!id,
    refetchInterval: (q) => {
      const s = q.state.data?.status;
      const terminal = s === 'completed' || s === 'failed' || s === 'cancelled';
      return terminal ? false : 1000;
    },
  });
}

/**
 * 任务列表。有进行中任务时自动 3s 轮询刷新进度（终态后停止），无 SSE 时的兜底实时性。
 */
export function useTaskList(query: TaskListQuery) {
  return useQuery({
    queryKey: taskKeys.list(query),
    queryFn: () => taskApi.list(query),
    refetchInterval: (q) => {
      const data = q.state.data;
      const hasRunning = (data?.records ?? []).some(
        (t) => t.status === 'running' || t.status === 'pending' || t.status === 'cancelling',
      );
      return hasRunning ? 3000 : false;
    },
  });
}

export function useCancelTask() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => taskApi.cancel(id),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: taskKeys.all });
    },
  });
}
