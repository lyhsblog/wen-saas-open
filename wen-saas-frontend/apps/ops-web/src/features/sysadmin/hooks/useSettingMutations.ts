import { App } from 'antd';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import {
  opsSettingApi,
  describeApiError,
  type OpsSettingsUpdateRequest,
  type SettingCategory,
} from '@wen-saas/api-client';
import { http } from '../../../lib/http';

export function useUpdateSettingCategory(category: SettingCategory) {
  const { message } = App.useApp();
  const qc = useQueryClient();
  return useMutation<void, Error, OpsSettingsUpdateRequest>({
    mutationFn: (body) => opsSettingApi(http).updateCategory(category, body),
    onSuccess: () => {
      void message.success('已保存');
      void qc.invalidateQueries({ queryKey: ['ops', 'settings'] });
    },
    onError: (err) => {
      void message.error(describeApiError(err, '保存失败'));
    },
  });
}
