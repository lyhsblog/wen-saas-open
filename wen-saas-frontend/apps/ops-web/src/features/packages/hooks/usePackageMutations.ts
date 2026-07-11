import { App } from 'antd';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import {
  opsPackageAdminApi,
  describeApiError,
  type PackageCopyRequest,
  type PackageCreateRequest,
  type PackageDetail,
  type PackageUpdateRequest,
} from '@wen-saas/api-client';
import { http } from '../../../lib/http';

function useInvalidate() {
  const qc = useQueryClient();
  return (code?: string) => {
    void qc.invalidateQueries({ queryKey: ['ops', 'packages'] });
    if (code) void qc.invalidateQueries({ queryKey: ['ops', 'package', code] });
  };
}

export function useCreatePackage() {
  const { message } = App.useApp();
  const invalidate = useInvalidate();
  return useMutation<PackageDetail, Error, PackageCreateRequest>({
    mutationFn: (body) => opsPackageAdminApi(http).createPackage(body),
    onSuccess: (detail) => {
      void message.success(detail.status === 'ACTIVE' ? '套餐已创建并上架' : '套餐已保存为草稿');
      invalidate(detail.code);
    },
    onError: (err) => {
      void message.error(describeApiError(err, '创建失败'));
    },
  });
}

export function useCopyPackage(sourceCode: string) {
  const { message } = App.useApp();
  const invalidate = useInvalidate();
  return useMutation<PackageDetail, Error, PackageCopyRequest>({
    mutationFn: (body) => opsPackageAdminApi(http).copyPackage(sourceCode, body),
    onSuccess: (detail) => {
      void message.success('套餐已复制为草稿');
      invalidate(detail.code);
    },
    // 错误交由表单 applyApiErrorToForm 处理字段级（如 409 CODE_CONFLICT），此处不重复弹 toast。
  });
}

export function useUpdatePackage(code: string) {
  const { message } = App.useApp();
  const invalidate = useInvalidate();
  return useMutation<PackageDetail, Error, PackageUpdateRequest>({
    mutationFn: (body) => opsPackageAdminApi(http).updatePackage(code, body),
    onSuccess: () => {
      void message.success('套餐已更新');
      invalidate(code);
    },
    onError: (err) => {
      void message.error(describeApiError(err, '更新失败'));
    },
  });
}

export function useDeletePackage() {
  const { message } = App.useApp();
  const invalidate = useInvalidate();
  return useMutation<void, Error, string>({
    mutationFn: (code) => opsPackageAdminApi(http).deletePackage(code),
    onSuccess: () => {
      void message.success('套餐已删除');
      invalidate();
    },
    onError: (err) => {
      void message.error(describeApiError(err, '删除失败'));
    },
  });
}

export function usePublishPackage() {
  const { message } = App.useApp();
  const invalidate = useInvalidate();
  return useMutation<void, Error, string>({
    mutationFn: (code) => opsPackageAdminApi(http).publish(code),
    onSuccess: (_data, code) => {
      void message.success('套餐已上架');
      invalidate(code);
    },
    onError: (err) => {
      void message.error(describeApiError(err, '上架失败'));
    },
  });
}

export function useUnpublishPackage() {
  const { message } = App.useApp();
  const invalidate = useInvalidate();
  return useMutation<void, Error, string>({
    mutationFn: (code) => opsPackageAdminApi(http).unpublish(code),
    onSuccess: (_data, code) => {
      void message.success('套餐已下架');
      invalidate(code);
    },
    onError: (err) => {
      void message.error(describeApiError(err, '下架失败'));
    },
  });
}

export function useArchivePackage() {
  const { message } = App.useApp();
  const invalidate = useInvalidate();
  return useMutation<void, Error, string>({
    mutationFn: (code) => opsPackageAdminApi(http).archive(code),
    onSuccess: (_data, code) => {
      void message.success('套餐已归档');
      invalidate(code);
    },
    onError: (err) => {
      void message.error(describeApiError(err, '归档失败'));
    },
  });
}
