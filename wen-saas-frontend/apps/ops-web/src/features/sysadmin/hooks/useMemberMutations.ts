import { App } from 'antd';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import {
  opsTeamMemberApi,
  describeApiError,
  type OpsMemberCreateRequest,
  type OpsMemberDetail,
  type OpsMemberResetPasswordRequest,
  type OpsMemberUpdateRequest,
} from '@wen-saas/api-client';
import { http } from '../../../lib/http';

function useInvalidate() {
  const qc = useQueryClient();
  return (id?: string) => {
    void qc.invalidateQueries({ queryKey: ['ops', 'team-members'] });
    if (id != null) void qc.invalidateQueries({ queryKey: ['ops', 'team-member', id] });
    // 角色成员数会随之变化。
    void qc.invalidateQueries({ queryKey: ['ops', 'team-roles'] });
  };
}

export function useCreateMember() {
  const { message } = App.useApp();
  const invalidate = useInvalidate();
  return useMutation<OpsMemberDetail, Error, OpsMemberCreateRequest>({
    mutationFn: (body) => opsTeamMemberApi(http).createMember(body),
    onSuccess: (detail) => {
      void message.success('成员已添加');
      invalidate(detail.id);
    },
    // 字段级错误（用户名重复 / 弱密码）交由表单 applyApiErrorToAntdForm 处理。
  });
}

export function useUpdateMember(userId: string) {
  const { message } = App.useApp();
  const invalidate = useInvalidate();
  return useMutation<OpsMemberDetail, Error, OpsMemberUpdateRequest>({
    mutationFn: (body) => opsTeamMemberApi(http).updateMember(userId, body),
    onSuccess: () => {
      void message.success('成员已更新');
      invalidate(userId);
    },
  });
}

export function useResetMemberPassword(userId: string) {
  const { message } = App.useApp();
  const invalidate = useInvalidate();
  return useMutation<void, Error, OpsMemberResetPasswordRequest>({
    mutationFn: (body) => opsTeamMemberApi(http).resetPassword(userId, body),
    onSuccess: () => {
      void message.success('密码已重置，该成员全部会话已下线');
      invalidate(userId);
    },
  });
}

/** 启停切换：enable=true 启用，否则停用。 */
export function useToggleMemberStatus() {
  const { message } = App.useApp();
  const invalidate = useInvalidate();
  return useMutation<void, Error, { userId: string; enable: boolean }>({
    mutationFn: ({ userId, enable }) =>
      enable
        ? opsTeamMemberApi(http).enableMember(userId)
        : opsTeamMemberApi(http).disableMember(userId),
    onSuccess: (_data, { userId, enable }) => {
      void message.success(enable ? '成员已启用' : '成员已停用');
      invalidate(userId);
    },
    onError: (err) => {
      void message.error(describeApiError(err, '操作失败'));
    },
  });
}

export function useUnbindMemberOauth() {
  const { message } = App.useApp();
  const invalidate = useInvalidate();
  return useMutation<void, Error, { userId: string; provider: string }>({
    mutationFn: ({ userId, provider }) => opsTeamMemberApi(http).unbindOauth(userId, provider),
    onSuccess: (_data, { userId }) => {
      void message.success('已解绑');
      invalidate(userId);
    },
    onError: (err) => {
      void message.error(describeApiError(err, '解绑失败'));
    },
  });
}
