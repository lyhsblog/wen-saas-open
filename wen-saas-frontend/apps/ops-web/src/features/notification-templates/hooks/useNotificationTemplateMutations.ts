import { App } from 'antd';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import {
  opsNotificationTemplateApi,
  describeApiError,
  type NotificationTemplateCreateRequest,
  type NotificationTemplateDetail,
  type NotificationTemplateUpdateRequest,
} from '@wen-saas/api-client';
import { http } from '../../../lib/http';

function useInvalidate() {
  const qc = useQueryClient();
  return (code?: string) => {
    void qc.invalidateQueries({ queryKey: ['ops', 'notification-templates'] });
    if (code) void qc.invalidateQueries({ queryKey: ['ops', 'notification-template', code] });
  };
}

export function useCreateTemplate() {
  const { message } = App.useApp();
  const invalidate = useInvalidate();
  return useMutation<NotificationTemplateDetail, Error, NotificationTemplateCreateRequest>({
    mutationFn: (body) => opsNotificationTemplateApi(http).createTemplate(body),
    onSuccess: (detail) => {
      void message.success('模板已创建');
      invalidate(detail.templateCode);
    },
    // 字段级错误（409 CODE_CONFLICT / 422）交由表单 applyApiErrorToAntdForm 处理，不重复 toast。
  });
}

export function useUpdateTemplate(code: string) {
  const { message } = App.useApp();
  const invalidate = useInvalidate();
  return useMutation<NotificationTemplateDetail, Error, NotificationTemplateUpdateRequest>({
    mutationFn: (body) => opsNotificationTemplateApi(http).updateTemplate(code, body),
    onSuccess: () => {
      void message.success('模板已更新');
      invalidate(code);
    },
    onError: (err) => {
      void message.error(describeApiError(err, '更新失败'));
    },
  });
}

export function useEnableTemplate() {
  const { message } = App.useApp();
  const invalidate = useInvalidate();
  return useMutation<void, Error, string>({
    mutationFn: (code) => opsNotificationTemplateApi(http).enable(code),
    onSuccess: (_data, code) => {
      void message.success('模板已启用');
      invalidate(code);
    },
    onError: (err) => {
      void message.error(describeApiError(err, '启用失败'));
    },
  });
}

export function useDisableTemplate() {
  const { message } = App.useApp();
  const invalidate = useInvalidate();
  return useMutation<void, Error, string>({
    mutationFn: (code) => opsNotificationTemplateApi(http).disable(code),
    onSuccess: (_data, code) => {
      void message.success('模板已停用');
      invalidate(code);
    },
    onError: (err) => {
      void message.error(describeApiError(err, '停用失败'));
    },
  });
}

export function useDeleteTemplate() {
  const { message } = App.useApp();
  const invalidate = useInvalidate();
  return useMutation<void, Error, string>({
    mutationFn: (code) => opsNotificationTemplateApi(http).deleteTemplate(code),
    onSuccess: () => {
      void message.success('模板已删除');
      invalidate();
    },
    onError: (err) => {
      void message.error(describeApiError(err, '删除失败'));
    },
  });
}
