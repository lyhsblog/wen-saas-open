import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { WebhookUpsertRequest } from '@wen-saas/api-client';
import { webhookApi } from './webhookApi';

export const webhookKeys = {
  all: ['webhooks'] as const,
  list: () => [...webhookKeys.all, 'list'] as const,
  logs: (id: string, page: number, size: number, finalStatus?: string) =>
    [...webhookKeys.all, 'logs', id, page, size, finalStatus ?? ''] as const,
};

export function useWebhookList() {
  return useQuery({ queryKey: webhookKeys.list(), queryFn: () => webhookApi.list() });
}

export function useWebhookLogs(
  id: string | null,
  page: number,
  size: number,
  finalStatus?: string,
) {
  return useQuery({
    queryKey: webhookKeys.logs(id ?? '__none__', page, size, finalStatus),
    queryFn: () =>
      webhookApi.logs(id!, { page, size, ...(finalStatus ? { finalStatus } : {}) }),
    enabled: !!id,
  });
}

function useInvalidatingMutation<TArgs, TResult>(fn: (v: TArgs) => Promise<TResult>) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: fn,
    onSuccess: () => qc.invalidateQueries({ queryKey: webhookKeys.all }),
  });
}

export function useCreateWebhook() {
  return useInvalidatingMutation((body: WebhookUpsertRequest) => webhookApi.create(body));
}

export function useUpdateWebhook() {
  return useInvalidatingMutation((v: { id: string; body: WebhookUpsertRequest }) =>
    webhookApi.update(v.id, v.body),
  );
}

export function useEnableWebhook() {
  return useInvalidatingMutation((id: string) => webhookApi.enable(id));
}

export function useDisableWebhook() {
  return useInvalidatingMutation((id: string) => webhookApi.disable(id));
}

export function useDeleteWebhook() {
  return useInvalidatingMutation((id: string) => webhookApi.remove(id));
}

export function useRotateWebhookSecret() {
  return useInvalidatingMutation((id: string) => webhookApi.rotateSecret(id));
}

export function useSendWebhookTest() {
  return useInvalidatingMutation((id: string) => webhookApi.sendTest(id));
}
