import { App } from 'antd';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import {
  opsTeamRoleApi,
  describeApiError,
  type OpsRoleCreateRequest,
  type OpsRoleDetail,
  type OpsRoleMembersRequest,
  type OpsRolePermissionsUpdateRequest,
  type OpsRoleUpdateRequest,
} from '@wen-saas/api-client';
import { http } from '../../../lib/http';

function useInvalidate() {
  const qc = useQueryClient();
  return (id?: string) => {
    void qc.invalidateQueries({ queryKey: ['ops', 'team-roles'] });
    if (id != null) {
      void qc.invalidateQueries({ queryKey: ['ops', 'team-role', id] });
      void qc.invalidateQueries({ queryKey: ['ops', 'team-role-members', id] });
    }
    // 成员的角色标签会随之变化。
    void qc.invalidateQueries({ queryKey: ['ops', 'team-members'] });
  };
}

export function useCreateRole() {
  const { message } = App.useApp();
  const invalidate = useInvalidate();
  return useMutation<OpsRoleDetail, Error, OpsRoleCreateRequest>({
    mutationFn: (body) => opsTeamRoleApi(http).createRole(body),
    onSuccess: (detail) => {
      void message.success('角色已创建');
      invalidate(detail.id);
    },
    // code 重复等字段级错误交由表单处理。
  });
}

export function useUpdateRole(roleId: string) {
  const { message } = App.useApp();
  const invalidate = useInvalidate();
  return useMutation<OpsRoleDetail, Error, OpsRoleUpdateRequest>({
    mutationFn: (body) => opsTeamRoleApi(http).updateRole(roleId, body),
    onSuccess: () => {
      void message.success('角色已更新');
      invalidate(roleId);
    },
  });
}

export function useDeleteRole() {
  const { message } = App.useApp();
  const invalidate = useInvalidate();
  return useMutation<void, Error, string>({
    mutationFn: (roleId) => opsTeamRoleApi(http).deleteRole(roleId),
    onSuccess: () => {
      void message.success('角色已删除');
      invalidate();
    },
    onError: (err) => {
      void message.error(describeApiError(err, '删除失败'));
    },
  });
}

export function useUpdateRolePermissions(roleId: string) {
  const { message } = App.useApp();
  const invalidate = useInvalidate();
  return useMutation<OpsRoleDetail, Error, OpsRolePermissionsUpdateRequest>({
    mutationFn: (body) => opsTeamRoleApi(http).updatePermissions(roleId, body),
    onSuccess: () => {
      void message.success('权限已保存');
      invalidate(roleId);
    },
    onError: (err) => {
      void message.error(describeApiError(err, '保存失败'));
    },
  });
}

export function useAssignRoleMembers(roleId: string) {
  const { message } = App.useApp();
  const invalidate = useInvalidate();
  return useMutation<void, Error, OpsRoleMembersRequest>({
    mutationFn: (body) => opsTeamRoleApi(http).assignMembers(roleId, body),
    onSuccess: () => {
      void message.success('成员已更新');
      invalidate(roleId);
    },
    onError: (err) => {
      void message.error(describeApiError(err, '保存失败'));
    },
  });
}
