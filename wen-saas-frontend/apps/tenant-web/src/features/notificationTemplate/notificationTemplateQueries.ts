import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { UpdateNotifyPrefRequest } from '@wen-saas/api-client';
import { notificationTemplateApi } from './notificationTemplateApi';

export const notifyTplKeys = {
  all: ['notification-template'] as const,
  list: (keyword: string) => ['notification-template', 'list', keyword] as const,
  channels: ['notification-template', 'channels'] as const,
  detail: (code: string) => ['notification-template', 'detail', code] as const,
};

export function useNotifyTemplateList(keyword: string) {
  return useQuery({
    queryKey: notifyTplKeys.list(keyword),
    queryFn: () => notificationTemplateApi.list(keyword ? { keyword } : undefined),
  });
}

export function useNotifyChannels() {
  return useQuery({
    queryKey: notifyTplKeys.channels,
    queryFn: () => notificationTemplateApi.channels(),
    staleTime: 5 * 60 * 1000,
  });
}

export function useNotifyTemplateDetail(code: string | null) {
  return useQuery({
    queryKey: notifyTplKeys.detail(code ?? ''),
    queryFn: () => notificationTemplateApi.get(code as string),
    enabled: !!code,
  });
}

export function useUpdateNotifyPref() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ code, body }: { code: string; body: UpdateNotifyPrefRequest }) =>
      notificationTemplateApi.updatePref(code, body),
    onSuccess: (_data, { code }) => {
      void qc.invalidateQueries({ queryKey: notifyTplKeys.detail(code) });
      void qc.invalidateQueries({ queryKey: notifyTplKeys.all });
    },
  });
}

export function useResetNotifyPref() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (code: string) => notificationTemplateApi.reset(code),
    onSuccess: (_data, code) => {
      void qc.invalidateQueries({ queryKey: notifyTplKeys.detail(code) });
      void qc.invalidateQueries({ queryKey: notifyTplKeys.all });
    },
  });
}
