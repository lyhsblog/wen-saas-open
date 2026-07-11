import { App } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  opsProfileApi,
  describeApiError,
  type ChangePasswordRequest,
  type NotificationPreferenceView,
  type OpsAvatarResult,
  type OpsLoginLogPageView,
  type OpsProfileBasic,
  type OpsProfileSecurity,
  type OpsProfileSso,
  type OpsProfileTeamsRoles,
  type OpsSessionItem,
  type UpdateNotificationPreferencesRequest,
  type UpdateOpsProfileRequest,
} from '@wen-saas/api-client';
import { http } from '../../lib/http';

export const profileKeys = {
  all: ['ops-profile'] as const,
  basic: () => [...profileKeys.all, 'basic'] as const,
  teamsRoles: () => [...profileKeys.all, 'teams-roles'] as const,
  security: () => [...profileKeys.all, 'security'] as const,
  sso: () => [...profileKeys.all, 'sso'] as const,
  sessions: () => [...profileKeys.all, 'sessions'] as const,
  loginLogs: (page: number, pageSize: number) =>
    [...profileKeys.all, 'login-logs', page, pageSize] as const,
  notifyPrefs: () => [...profileKeys.all, 'notify-prefs'] as const,
};

const api = () => opsProfileApi(http);

// ===================== queries =====================
// 个人中心按 tab 拆接口。basic / teamsRoles 常驻（左侧卡片要用），
// security / sso 由各自 section 在 tab 激活、组件挂载时才拉，保证切换实时性。

export function useBasicQuery() {
  return useQuery<OpsProfileBasic>({
    queryKey: profileKeys.basic(),
    queryFn: () => api().getBasic(),
  });
}

export function useTeamsRolesQuery() {
  return useQuery<OpsProfileTeamsRoles>({
    queryKey: profileKeys.teamsRoles(),
    queryFn: () => api().getTeamsRoles(),
  });
}

export function useSecurityQuery() {
  return useQuery<OpsProfileSecurity>({
    queryKey: profileKeys.security(),
    queryFn: () => api().getSecurity(),
  });
}

export function useSsoQuery() {
  return useQuery<OpsProfileSso>({
    queryKey: profileKeys.sso(),
    queryFn: () => api().getSso(),
  });
}

export function useSessionsQuery() {
  return useQuery<OpsSessionItem[]>({
    queryKey: profileKeys.sessions(),
    queryFn: () => api().listSessions(),
  });
}

export function useLoginLogsQuery(page: number, pageSize: number) {
  return useQuery<OpsLoginLogPageView>({
    queryKey: profileKeys.loginLogs(page, pageSize),
    queryFn: () => api().listLoginLogs({ page, pageSize }),
  });
}

export function useNotifyPrefsQuery() {
  return useQuery<NotificationPreferenceView>({
    queryKey: profileKeys.notifyPrefs(),
    queryFn: () => api().getNotificationPreferences(),
  });
}

// ===================== mutations =====================

export function useUpdateProfile() {
  const { message } = App.useApp();
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: UpdateOpsProfileRequest) => api().updateProfile(body),
    onSuccess: () => {
      void message.success('基本信息已更新');
      void qc.invalidateQueries({ queryKey: profileKeys.basic() });
    },
    onError: (err) => {
      void message.error(describeApiError(err, '更新失败'));
    },
  });
}

export function useUploadAvatar() {
  const { message } = App.useApp();
  const qc = useQueryClient();
  return useMutation<OpsAvatarResult, unknown, File>({
    mutationFn: (file: File) => api().uploadAvatar(file),
    onSuccess: () => {
      void message.success('头像已上传');
      void qc.invalidateQueries({ queryKey: profileKeys.basic() });
    },
    onError: (err) => {
      void message.error(describeApiError(err, '头像上传失败'));
    },
  });
}

export function useChangePassword() {
  const { message } = App.useApp();
  return useMutation({
    mutationFn: (body: ChangePasswordRequest) => api().changePassword(body),
    onError: (err) => {
      void message.error(describeApiError(err, '修改失败'));
    },
  });
}

export function useRevokeSession() {
  const { message } = App.useApp();
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (sessionId: string) => api().revokeSession(sessionId),
    onSuccess: () => {
      void message.success('该设备已下线');
      void qc.invalidateQueries({ queryKey: profileKeys.sessions() });
    },
    onError: (err) => {
      void message.error(describeApiError(err, '下线失败'));
    },
  });
}

export function useRevokeOtherSessions() {
  const { message } = App.useApp();
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => api().revokeOtherSessions(),
    onSuccess: () => {
      void message.success('其他设备已全部下线');
      void qc.invalidateQueries({ queryKey: profileKeys.sessions() });
    },
    onError: (err) => {
      void message.error(describeApiError(err, '操作失败'));
    },
  });
}

export function useUpdateNotifyPrefs() {
  const { message } = App.useApp();
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: UpdateNotificationPreferencesRequest) =>
      api().updateNotificationPreferences(body),
    onSuccess: () => {
      void message.success('通知偏好已保存');
      void qc.invalidateQueries({ queryKey: profileKeys.notifyPrefs() });
    },
    onError: (err) => {
      void message.error(describeApiError(err, '保存失败'));
    },
  });
}
