import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type {
  MemberCreateRequest,
  MemberUpdateRequest,
  MemberDeptsRequest,
  MemberListQuery,
} from '@wen-saas/api-client';
import { memberApi } from './memberApi';
import { deptKeys } from '../dept/deptQueries';

export const memberKeys = {
  all: ['member'] as const,
  list: (q: MemberListQuery) => [...memberKeys.all, 'list', q] as const,
  detail: (userId: string) => [...memberKeys.all, 'detail', userId] as const,
  oauth: (userId: string) => [...memberKeys.all, 'oauth', userId] as const,
  loginLogs: (userId: string) => [...memberKeys.all, 'login-logs', userId] as const,
};

export function useMemberOAuthBindings(userId: string | null, enabled = true) {
  return useQuery({
    queryKey: memberKeys.oauth(userId ?? '__none__'),
    queryFn: () => memberApi.oauthBindings(userId!),
    enabled: !!userId && enabled,
  });
}

export function useMemberLoginLogs(userId: string | null, limit = 20, enabled = true) {
  return useQuery({
    queryKey: [...memberKeys.loginLogs(userId ?? '__none__'), limit],
    queryFn: () => memberApi.loginLogs(userId!, limit),
    enabled: !!userId && enabled,
  });
}

export function useMemberList(query: MemberListQuery, enabled = true) {
  return useQuery({
    queryKey: memberKeys.list(query),
    queryFn: () => memberApi.list(query),
    enabled,
  });
}

export function useMemberDetail(userId: string | null) {
  return useQuery({
    queryKey: memberKeys.detail(userId ?? '__none__'),
    queryFn: () => memberApi.detail(userId!),
    enabled: !!userId,
  });
}

function useInvalidate() {
  const qc = useQueryClient();
  return (userId?: string) => {
    void qc.invalidateQueries({ queryKey: memberKeys.all });
    if (userId) void qc.invalidateQueries({ queryKey: memberKeys.detail(userId) });
    // 部门树头部「成员 X 人」计数随增删/停用成员变化（T2-01）：一并失效部门树
    void qc.invalidateQueries({ queryKey: deptKeys.tree() });
  };
}

export function useCreateMember() {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: (body: MemberCreateRequest) => memberApi.create(body),
    onSuccess: () => invalidate(),
  });
}

export function useUpdateMember(userId: string) {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: (body: MemberUpdateRequest) => memberApi.update(userId, body),
    onSuccess: () => invalidate(userId),
  });
}

export function useAssignMemberRoles(userId: string) {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: (roleIds: string[]) => memberApi.assignRoles(userId, roleIds),
    onSuccess: () => invalidate(userId),
  });
}

export function useManageMemberDepts(userId: string) {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: (body: MemberDeptsRequest) => memberApi.manageDepts(userId, body),
    onSuccess: () => invalidate(userId),
  });
}

export function useSetMemberStatus() {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: ({ userId, enabled }: { userId: string; enabled: boolean }) =>
      memberApi.setStatus(userId, enabled),
    onSuccess: (_d, v) => invalidate(v.userId),
  });
}

export function useReissueActivation() {
  return useMutation({
    mutationFn: (userId: string) => memberApi.reissueActivation(userId),
  });
}

export function useUnlockMemberLogin() {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: (userId: string) => memberApi.unlockLogin(userId),
    onSuccess: (_d, userId) => invalidate(userId),
  });
}
