import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type {
  CrmCustomerCreateRequest,
  CrmCustomerListQuery,
  CrmCustomerMineQuery,
  CrmCustomerUpdateRequest,
} from '@wen-saas/api-client';
import { crmApi } from './crmApi';

export const crmKeys = {
  all: ['crm-customer'] as const,
  list: (q: CrmCustomerListQuery, cf: Record<string, string>) =>
    ['crm-customer', 'list', q, cf] as const,
  mine: (q: CrmCustomerMineQuery, cf: Record<string, string>) =>
    ['crm-customer', 'mine', q, cf] as const,
  detail: (id: string) => ['crm-customer', 'detail', id] as const,
};

/** 管理列表（需 crm_demo.customer.view_manage）。 */
export function useCustomerList(
  query: CrmCustomerListQuery,
  cfEquals: Record<string, string> = {},
) {
  return useQuery({
    queryKey: crmKeys.list(query, cfEquals),
    queryFn: () => crmApi.list(query, cfEquals),
  });
}

/** 我的客户（需 crm_demo.customer.view）。 */
export function useMineCustomerList(
  query: CrmCustomerMineQuery,
  cfEquals: Record<string, string> = {},
) {
  return useQuery({
    queryKey: crmKeys.mine(query, cfEquals),
    queryFn: () => crmApi.listMine(query, cfEquals),
  });
}

export function useCustomerDetail(id: string | null) {
  return useQuery({
    queryKey: crmKeys.detail(id ?? '__none__'),
    queryFn: () => crmApi.get(id!),
    enabled: !!id,
  });
}

export function useCreateCustomer() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: CrmCustomerCreateRequest) => crmApi.create(body),
    onSuccess: () => qc.invalidateQueries({ queryKey: crmKeys.all }),
  });
}

export function useUpdateCustomer() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (v: { id: string; body: CrmCustomerUpdateRequest }) => crmApi.update(v.id, v.body),
    onSuccess: () => qc.invalidateQueries({ queryKey: crmKeys.all }),
  });
}

export function useDeleteCustomer() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => crmApi.remove(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: crmKeys.all }),
  });
}

export function useTransitionCustomer() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (v: { id: string; target: string }) => crmApi.transition(v.id, v.target),
    onSuccess: () => qc.invalidateQueries({ queryKey: crmKeys.all }),
  });
}
