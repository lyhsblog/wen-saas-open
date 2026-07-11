import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { App } from 'antd';
import type {
  ExportRequestQuery,
  SubmitDataExportRequest,
  SubmitExportRequest,
  UpdateExportPolicyRequest,
} from '@wen-saas/api-client';
import { complianceApi } from './complianceApi';

export const complianceKeys = {
  all: ['compliance'] as const,
  overview: () => [...complianceKeys.all, 'overview'] as const,
  myList: (q: ExportRequestQuery) => [...complianceKeys.all, 'my', q] as const,
  allList: (q: ExportRequestQuery) => [...complianceKeys.all, 'all', q] as const,
  detail: (id: string) => [...complianceKeys.all, 'detail', id] as const,
  policy: () => [...complianceKeys.all, 'policy'] as const,
  dataExports: (page: number) => [...complianceKeys.all, 'dataExports', page] as const,
  dataExportDetail: (id: string) => [...complianceKeys.all, 'dataExportDetail', id] as const,
  retention: () => [...complianceKeys.all, 'retention'] as const,
  encryption: () => [...complianceKeys.all, 'encryption'] as const,
};

export function useComplianceOverview(enabled = true) {
  return useQuery({
    queryKey: complianceKeys.overview(),
    queryFn: () => complianceApi.overview(),
    enabled,
  });
}

export function useMyExportRequests(query: ExportRequestQuery, enabled = true) {
  return useQuery({
    queryKey: complianceKeys.myList(query),
    queryFn: () => complianceApi.myRequests(query),
    enabled,
  });
}

export function useAllExportRequests(query: ExportRequestQuery, enabled: boolean) {
  return useQuery({
    queryKey: complianceKeys.allList(query),
    queryFn: () => complianceApi.allRequests(query),
    enabled,
  });
}

export function useExportRequestDetail(id: string | null) {
  return useQuery({
    queryKey: id ? complianceKeys.detail(id) : complianceKeys.detail('__none__'),
    queryFn: () => complianceApi.getDetail(id!),
    enabled: !!id,
  });
}

export function useExportPolicy(enabled = true) {
  return useQuery({
    queryKey: complianceKeys.policy(),
    queryFn: () => complianceApi.getPolicy(),
    enabled,
  });
}

export function useSubmitExportRequest() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: SubmitExportRequest) => complianceApi.submit(body),
    onSuccess: () => qc.invalidateQueries({ queryKey: complianceKeys.all }),
  });
}

export function useDecideExportRequest(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: { approve: boolean; comment?: string }) =>
      input.approve
        ? complianceApi.approve(id, input.comment)
        : complianceApi.reject(id, input.comment),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: complianceKeys.detail(id) });
      void qc.invalidateQueries({ queryKey: complianceKeys.all });
    },
  });
}

export function useDataExports(page: number, enabled = true) {
  return useQuery({
    queryKey: complianceKeys.dataExports(page),
    queryFn: () => complianceApi.listDataExports(page),
    enabled,
    // 有打包中的申请时自动轮询，待异步任务完成后刷新为已完成
    refetchInterval: (query) =>
      query.state.data?.items.some((i) => i.status === 'PACKAGING') ? 2000 : false,
  });
}

export function useDataExportDetail(id: string | null) {
  return useQuery({
    queryKey: id
      ? complianceKeys.dataExportDetail(id)
      : complianceKeys.dataExportDetail('__none__'),
    queryFn: () => complianceApi.getDataExportDetail(id!),
    enabled: !!id,
    refetchInterval: (query) => (query.state.data?.status === 'PACKAGING' ? 2000 : false),
  });
}

export function useDownloadDataExport() {
  const { message } = App.useApp();
  return useMutation({
    mutationFn: (id: string) => complianceApi.downloadDataExportUrl(id),
    onSuccess: (url) => {
      const a = document.createElement('a');
      a.href = url;
      a.rel = 'noopener';
      document.body.appendChild(a);
      a.click();
      a.remove();
    },
    onError: () => {
      message.error('下载失败，导出包可能尚未完成或链接已过期');
    },
  });
}

export function useSubmitDataExport() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: SubmitDataExportRequest) => complianceApi.submitDataExport(body),
    onSuccess: () => qc.invalidateQueries({ queryKey: complianceKeys.all }),
  });
}

export function useUpdateExportPolicy() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: UpdateExportPolicyRequest) => complianceApi.updatePolicy(body),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: complianceKeys.policy() });
      void qc.invalidateQueries({ queryKey: complianceKeys.overview() });
    },
  });
}
