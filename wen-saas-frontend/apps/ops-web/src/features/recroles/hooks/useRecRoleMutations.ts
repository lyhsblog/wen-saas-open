import { App } from 'antd';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import {
  opsRecommendedRoleAdminApi,
  describeApiError,
  type RecRoleCopyRequest,
  type RecRoleCreateRequest,
  type RecRoleDetail,
  type RecRoleUpdateRequest,
} from '@wen-saas/api-client';
import { http } from '../../../lib/http';

function useInvalidate() {
  const qc = useQueryClient();
  return (id?: string) => {
    void qc.invalidateQueries({ queryKey: ['ops', 'rec-roles'] });
    if (id != null) void qc.invalidateQueries({ queryKey: ['ops', 'rec-role', id] });
  };
}

export function useCreateRecRole() {
  const { message } = App.useApp();
  const invalidate = useInvalidate();
  return useMutation<RecRoleDetail, Error, RecRoleCreateRequest>({
    mutationFn: (body) => opsRecommendedRoleAdminApi(http).createTemplate(body),
    onSuccess: (detail) => {
      void message.success(detail.status === 'PUBLISHED' ? '模板已创建并发布' : '模板已保存为草稿');
      invalidate(detail.id);
    },
    onError: (err) => {
      void message.error(describeApiError(err, '创建失败'));
    },
  });
}

export function useUpdateRecRole(id: string) {
  const { message } = App.useApp();
  const invalidate = useInvalidate();
  return useMutation<RecRoleDetail, Error, RecRoleUpdateRequest>({
    mutationFn: (body) => opsRecommendedRoleAdminApi(http).updateTemplate(id, body),
    onSuccess: () => {
      void message.success('模板已更新');
      invalidate(id);
    },
    onError: (err) => {
      void message.error(describeApiError(err, '更新失败'));
    },
  });
}

export function useCopyRecRole(sourceId: string) {
  const { message } = App.useApp();
  const invalidate = useInvalidate();
  return useMutation<RecRoleDetail, Error, RecRoleCopyRequest>({
    mutationFn: (body) => opsRecommendedRoleAdminApi(http).copyTemplate(sourceId, body),
    onSuccess: (detail) => {
      void message.success('模板已复制为草稿');
      invalidate(detail.id);
    },
    // 名称冲突等字段级错误交由表单 applyApiErrorToAntdForm 处理，此处不重复弹 toast。
  });
}

export function useDeleteRecRole() {
  const { message } = App.useApp();
  const invalidate = useInvalidate();
  return useMutation<void, Error, string>({
    mutationFn: (id) => opsRecommendedRoleAdminApi(http).deleteTemplate(id),
    onSuccess: () => {
      void message.success('模板已删除');
      invalidate();
    },
    onError: (err) => {
      void message.error(describeApiError(err, '删除失败'));
    },
  });
}

export function usePublishRecRole() {
  const { message } = App.useApp();
  const invalidate = useInvalidate();
  return useMutation<void, Error, string>({
    mutationFn: (id) => opsRecommendedRoleAdminApi(http).publish(id),
    onSuccess: (_data, id) => {
      void message.success('模板已发布');
      invalidate(id);
    },
    onError: (err) => {
      void message.error(describeApiError(err, '发布失败'));
    },
  });
}

export function useUnpublishRecRole() {
  const { message } = App.useApp();
  const invalidate = useInvalidate();
  return useMutation<void, Error, string>({
    mutationFn: (id) => opsRecommendedRoleAdminApi(http).unpublish(id),
    onSuccess: (_data, id) => {
      void message.success('模板已下架');
      invalidate(id);
    },
    onError: (err) => {
      void message.error(describeApiError(err, '下架失败'));
    },
  });
}
