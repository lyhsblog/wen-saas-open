import { useQuery, useQueryClient } from '@tanstack/react-query';
import { profileApi } from './profileApi';

export const profileKeys = {
  all: ['tenant-profile'] as const,
  basic: ['tenant-profile', 'basic'] as const,
  work: ['tenant-profile', 'work'] as const,
  security: ['tenant-profile', 'security'] as const,
  sessions: ['tenant-profile', 'sessions'] as const,
  mfa: ['tenant-profile', 'mfa'] as const,
  oauthBindings: ['tenant-profile', 'oauth-bindings'] as const,
};

export function useBasic() {
  return useQuery({ queryKey: profileKeys.basic, queryFn: () => profileApi.getBasic() });
}

export function useWork(enabled: boolean) {
  return useQuery({ queryKey: profileKeys.work, queryFn: () => profileApi.getWork(), enabled });
}

export function useSecurity(enabled: boolean) {
  return useQuery({
    queryKey: profileKeys.security,
    queryFn: () => profileApi.getSecurity(),
    enabled,
  });
}

export function useSessions(enabled: boolean) {
  return useQuery({
    queryKey: profileKeys.sessions,
    queryFn: () => profileApi.listSessions(),
    enabled,
  });
}

export function useOAuthBindings(enabled: boolean) {
  return useQuery({
    queryKey: profileKeys.oauthBindings,
    queryFn: () => profileApi.listOAuthBindings(),
    enabled,
  });
}

export function useInvalidateProfile() {
  const qc = useQueryClient();
  return () => qc.invalidateQueries({ queryKey: profileKeys.all });
}
