import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { CreateFieldRequest } from '@wen-saas/api-client';
import { cfApi } from './customFieldApi';
import { dictApi } from '../dict/dictApi';

export const cfKeys = {
  objects: ['cf', 'objects'] as const,
  refObjects: ['cf', 'ref-objects'] as const,
  dictGroups: ['cf', 'dict-groups'] as const,
  quota: ['cf', 'quota'] as const,
  fields: (objectCode: string) => ['cf', 'fields', objectCode] as const,
};

export function useCfObjects() {
  return useQuery({ queryKey: cfKeys.objects, queryFn: () => cfApi.listObjects() });
}

/**
 * REF 字段可关联的对象 = 后端 `/ref-objects`：内建数据实体（成员 / 部门）+ 已注册业务对象。
 * 不含角色（权限构件、非数据记录）/ platform-only 实体——故不能直接用实体反查 `/types`（那是「可搜索类型」，含角色）。
 */
export function useRefObjectOptions() {
  return useQuery({
    queryKey: cfKeys.refObjects,
    queryFn: () => cfApi.listRefObjects(),
    staleTime: 5 * 60 * 1000,
  });
}

/** ENUM 字段可关联的字典分组（含 SYSTEM/MODULE/CUSTOM，当前租户视角）。 */
export function useDictGroups() {
  return useQuery({
    queryKey: cfKeys.dictGroups,
    queryFn: () => dictApi.listGroups(),
    staleTime: 5 * 60 * 1000,
  });
}

export function useCfQuota() {
  return useQuery({ queryKey: cfKeys.quota, queryFn: () => cfApi.quota() });
}

export function useCfFields(objectCode: string | null, enabled = true) {
  return useQuery({
    queryKey: cfKeys.fields(objectCode ?? '__none__'),
    queryFn: () => cfApi.listFields(objectCode!),
    enabled: !!objectCode && enabled,
  });
}

function invalidate(objectCode: string) {
  return (qc: ReturnType<typeof useQueryClient>) => {
    void qc.invalidateQueries({ queryKey: cfKeys.fields(objectCode) });
    void qc.invalidateQueries({ queryKey: cfKeys.quota });
  };
}

export function useCreateField() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateFieldRequest) => cfApi.createField(body),
    onSuccess: (_r, body) => invalidate(body.objectCode)(qc),
  });
}

export function useRenameField(objectCode: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (v: { id: string; name: string }) => cfApi.renameField(v.id, v.name),
    onSuccess: () => invalidate(objectCode)(qc),
  });
}

export function useToggleIndex(objectCode: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (v: { id: string; indexed: boolean }) => cfApi.toggleIndex(v.id, v.indexed),
    onSuccess: () => invalidate(objectCode)(qc),
  });
}

export function useDeleteField(objectCode: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => cfApi.deleteField(id),
    onSuccess: () => invalidate(objectCode)(qc),
  });
}
