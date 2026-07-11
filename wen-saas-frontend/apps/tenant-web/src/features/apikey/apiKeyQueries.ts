import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  tenantApiKeyApi,
  type ApiCallLogQuery,
  type ApiKeyUpsertRequest,
} from '@wen-saas/api-client';
import { http } from '../../lib/http';

export const apiKeyApi = tenantApiKeyApi(http);

export const apiKeyKeys = {
  all: ['api-keys'] as const,
  list: () => [...apiKeyKeys.all, 'list'] as const,
  callLogs: (q: ApiCallLogQuery) => [...apiKeyKeys.all, 'call-logs', q] as const,
};

export function useApiKeyList() {
  return useQuery({ queryKey: apiKeyKeys.list(), queryFn: () => apiKeyApi.list() });
}

export function useApiCallLogs(query: ApiCallLogQuery) {
  return useQuery({
    queryKey: apiKeyKeys.callLogs(query),
    queryFn: () => apiKeyApi.callLogs(query),
  });
}

function useInvalidatingMutation<TArgs, TResult>(fn: (v: TArgs) => Promise<TResult>) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: fn,
    onSuccess: () => qc.invalidateQueries({ queryKey: apiKeyKeys.all }),
  });
}

export function useCreateApiKey() {
  return useInvalidatingMutation((body: ApiKeyUpsertRequest) => apiKeyApi.create(body));
}

export function useUpdateApiKey() {
  return useInvalidatingMutation((v: { id: string; body: ApiKeyUpsertRequest }) =>
    apiKeyApi.update(v.id, v.body),
  );
}

export function useEnableApiKey() {
  return useInvalidatingMutation((id: string) => apiKeyApi.enable(id));
}

export function useDisableApiKey() {
  return useInvalidatingMutation((id: string) => apiKeyApi.disable(id));
}

export function useRotateApiKeySecret() {
  return useInvalidatingMutation((id: string) => apiKeyApi.rotateSecret(id));
}
