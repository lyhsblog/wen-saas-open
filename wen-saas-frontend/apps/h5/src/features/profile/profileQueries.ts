import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { ChangePasswordRequest } from '@wen-saas/api-client';
import { profileApi } from '../../lib/api';

export function useProfileBasic() {
  return useQuery({ queryKey: ['profile', 'basic'], queryFn: () => profileApi.getBasic() });
}

export function useProfileWork() {
  return useQuery({ queryKey: ['profile', 'work'], queryFn: () => profileApi.getWork() });
}

export function useProfileSecurity() {
  return useQuery({ queryKey: ['profile', 'security'], queryFn: () => profileApi.getSecurity() });
}

export function useSessions() {
  return useQuery({ queryKey: ['profile', 'sessions'], queryFn: () => profileApi.listSessions() });
}

export function useOAuthBindings() {
  return useQuery({
    queryKey: ['profile', 'oauth'],
    queryFn: () => profileApi.listOAuthBindings(),
  });
}

export function useChangePassword() {
  return useMutation({
    mutationFn: (body: ChangePasswordRequest) => profileApi.changePassword(body),
  });
}

export function useBindOAuth() {
  const qc = useQueryClient();
  return useMutation({
    // mock 后端约定 authCode；真实接入后改为渠道 SDK 换取的 code（R2 剩余项）。
    mutationFn: (provider: string) => profileApi.bindOAuth(provider, `mock-${provider}-profile`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['profile', 'oauth'] }),
  });
}

export function useUnbindOAuth() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (provider: string) => profileApi.unbindOAuth(provider),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['profile', 'oauth'] }),
  });
}
