import { useQuery } from '@tanstack/react-query';
import { tenantWorkbenchApi } from '@wen-saas/api-client';
import { http } from '../../lib/http';

const api = tenantWorkbenchApi(http);

export const workbenchKeys = {
  all: ['tenant', 'workbench'] as const,
  overview: () => [...workbenchKeys.all, 'overview'] as const,
  cards: () => [...workbenchKeys.all, 'cards'] as const,
};

export function useWorkbenchOverview() {
  return useQuery({
    queryKey: workbenchKeys.overview(),
    queryFn: () => api.overview(),
  });
}

export function useWorkbenchCards() {
  return useQuery({
    queryKey: workbenchKeys.cards(),
    queryFn: () => api.cards(),
  });
}
