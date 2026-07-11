import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type {
  DictGroup,
  DictGroupCreateRequest,
  DictGroupUpdateRequest,
  DictItem,
  DictItemCreateRequest,
  DictItemUpdateRequest,
} from '@wen-saas/domain';
import { dictApi } from './dictApi';

export const dictKeys = {
  all: ['dict'] as const,
  groups: () => [...dictKeys.all, 'groups'] as const,
  items: (groupCode: string) => [...dictKeys.all, 'items', groupCode] as const,
};

export function useDictGroups() {
  return useQuery<DictGroup[]>({
    queryKey: dictKeys.groups(),
    queryFn: () => dictApi.listGroups(),
  });
}

export function useDictItems(groupCode: string | null) {
  return useQuery<DictItem[]>({
    queryKey: groupCode ? dictKeys.items(groupCode) : dictKeys.items('__none__'),
    queryFn: () => dictApi.listItems(groupCode!),
    enabled: !!groupCode,
  });
}

export function useCreateDictGroup() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: DictGroupCreateRequest) => dictApi.createGroup(body),
    onSuccess: () => qc.invalidateQueries({ queryKey: dictKeys.groups() }),
  });
}

export function useUpdateDictGroup() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (vars: { code: string; body: DictGroupUpdateRequest }) =>
      dictApi.updateGroup(vars.code, vars.body),
    onSuccess: () => qc.invalidateQueries({ queryKey: dictKeys.groups() }),
  });
}

export function useDeleteDictGroup() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (code: string) => dictApi.deleteGroup(code),
    onSuccess: () => qc.invalidateQueries({ queryKey: dictKeys.groups() }),
  });
}

export function useCreateDictItem(groupCode: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: DictItemCreateRequest) => dictApi.createItem(groupCode, body),
    onSuccess: () => qc.invalidateQueries({ queryKey: dictKeys.items(groupCode) }),
  });
}

export function useUpdateDictItem(groupCode: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (vars: { id: number; body: DictItemUpdateRequest }) =>
      dictApi.updateItem(vars.id, vars.body),
    onSuccess: () => qc.invalidateQueries({ queryKey: dictKeys.items(groupCode) }),
  });
}

export function useDeleteDictItem(groupCode: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => dictApi.deleteItem(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: dictKeys.items(groupCode) }),
  });
}

export function useSetDictItemStatus(groupCode: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (vars: { id: number; enabled: boolean }) =>
      dictApi.setItemStatus(vars.id, vars.enabled),
    onSuccess: () => qc.invalidateQueries({ queryKey: dictKeys.items(groupCode) }),
  });
}
