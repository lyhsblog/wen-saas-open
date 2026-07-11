import { useQuery } from '@tanstack/react-query';
import { auditApi, type AuditScope, type LogQuery } from './auditApi';

export function useOperationLogs(scope: AuditScope, q: LogQuery) {
  return useQuery({
    queryKey: ['audit', scope, q],
    queryFn: () => auditApi.list(scope, q),
  });
}

export function useOperationLogDetail(
  scope: AuditScope,
  id: string | null,
  createdAt: string | null,
) {
  return useQuery({
    queryKey: ['audit', scope, 'detail', id, createdAt],
    queryFn: () => auditApi.detail(scope, id!, createdAt!),
    enabled: !!id && !!createdAt,
  });
}
