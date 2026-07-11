import { useQuery } from '@tanstack/react-query';
import { workbenchApi } from '../../lib/api';

export function useWorkbenchOverview() {
  return useQuery({
    queryKey: ['workbench', 'overview'],
    queryFn: () => workbenchApi.overview(),
  });
}

export function useWorkbenchCards() {
  return useQuery({
    queryKey: ['workbench', 'cards'],
    queryFn: () => workbenchApi.cards(),
  });
}
