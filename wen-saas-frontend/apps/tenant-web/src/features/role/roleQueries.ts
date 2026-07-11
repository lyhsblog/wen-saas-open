import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type {
  RoleCreateRequest,
  RoleUpdateRequest,
  RolePermissionsUpdateRequest,
  MemberIdsRequest,
} from '@wen-saas/domain';
import type { SaveDataScopeCmd } from '@wen-saas/api-client';
import { roleApi } from './roleApi';

export const roleKeys = {
  all: ['role'] as const,
  list: () => [...roleKeys.all, 'list'] as const,
  detail: (roleId: string) => [...roleKeys.all, 'detail', roleId] as const,
  members: (roleId: string) => [...roleKeys.all, 'members', roleId] as const,
  dataScope: (roleId: string) => [...roleKeys.all, 'data-scope', roleId] as const,
  dataPermissionActions: () => [...roleKeys.all, 'data-permission-actions'] as const,
};

export function useRoles() {
  return useQuery({
    queryKey: roleKeys.list(),
    queryFn: () => roleApi.listRoles(),
  });
}

export function useRoleDetail(roleId: string | null) {
  return useQuery({
    queryKey: roleId ? roleKeys.detail(roleId) : roleKeys.detail('__none__'),
    queryFn: () => roleApi.getRole(roleId!),
    enabled: !!roleId,
  });
}

export function useRoleMembers(roleId: string | null) {
  return useQuery({
    queryKey: roleId ? roleKeys.members(roleId) : roleKeys.members('__none__'),
    queryFn: () => roleApi.listRoleMembers(roleId!),
    enabled: !!roleId,
  });
}

export function useCreateRole() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: RoleCreateRequest) => roleApi.createRole(body),
    // 用 void 不阻塞 mutateAsync：弹窗成功即关、按钮不再持续 loading，列表后台重拉（T3-02）
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: roleKeys.list() });
    },
  });
}

export function useUpdateRole(roleId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: RoleUpdateRequest) => roleApi.updateRole(roleId, body),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: roleKeys.list() });
      void qc.invalidateQueries({ queryKey: roleKeys.detail(roleId) });
    },
  });
}

export function useDeleteRole() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (roleId: string) => roleApi.deleteRole(roleId),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: roleKeys.list() });
    },
  });
}

export function useSetRoleStatus(roleId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (enabled: boolean) => roleApi.setRoleStatus(roleId, enabled),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: roleKeys.list() });
      void qc.invalidateQueries({ queryKey: roleKeys.detail(roleId) });
    },
  });
}

export function useUpdateRolePermissions(roleId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: RolePermissionsUpdateRequest) => roleApi.updateRolePermissions(roleId, body),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: roleKeys.detail(roleId) });
    },
  });
}

export function useAddRoleMembers(roleId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: MemberIdsRequest) => roleApi.addRoleMembers(roleId, body),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: roleKeys.list() });
      void qc.invalidateQueries({ queryKey: roleKeys.detail(roleId) });
      void qc.invalidateQueries({ queryKey: roleKeys.members(roleId) });
    },
  });
}

export function useRemoveRoleMembers(roleId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: MemberIdsRequest) => roleApi.removeRoleMembers(roleId, body),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: roleKeys.list() });
      void qc.invalidateQueries({ queryKey: roleKeys.detail(roleId) });
      void qc.invalidateQueries({ queryKey: roleKeys.members(roleId) });
    },
  });
}

export function useRoleDataScope(roleId: string | null) {
  return useQuery({
    queryKey: roleId ? roleKeys.dataScope(roleId) : roleKeys.dataScope('__none__'),
    queryFn: () => roleApi.getRoleDataScope(roleId!),
    enabled: !!roleId,
  });
}

export function useSaveRoleDataScope(roleId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: SaveDataScopeCmd) => roleApi.saveRoleDataScope(roleId, body),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: roleKeys.dataScope(roleId) });
      void qc.invalidateQueries({ queryKey: roleKeys.detail(roleId) });
      void qc.invalidateQueries({ queryKey: roleKeys.list() });
    },
  });
}

export function useDataPermissionActions() {
  return useQuery({
    queryKey: roleKeys.dataPermissionActions(),
    queryFn: () => roleApi.listDataPermissionActions(),
    staleTime: 5 * 60 * 1000, // action 列表启动后不变，5 min stale 避免重复请求
  });
}
