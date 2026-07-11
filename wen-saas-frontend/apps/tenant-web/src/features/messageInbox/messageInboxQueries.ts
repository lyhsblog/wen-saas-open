import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { InboxQuery, MessageCategory } from '@wen-saas/api-client';
import { messageInboxApi } from './messageInboxApi';

export const inboxKeys = {
  all: ['messageInbox'] as const,
  list: (q: InboxQuery) => [...inboxKeys.all, 'list', q] as const,
  detail: (id: string) => [...inboxKeys.all, 'detail', id] as const,
  deliveries: (id: string) => [...inboxKeys.all, 'deliveries', id] as const,
  unread: () => [...inboxKeys.all, 'unread'] as const,
};

export function useInboxList(query: InboxQuery) {
  return useQuery({ queryKey: inboxKeys.list(query), queryFn: () => messageInboxApi.list(query) });
}

export function useMessageDetail(id: string | null) {
  return useQuery({
    queryKey: inboxKeys.detail(id ?? '__none__'),
    queryFn: () => messageInboxApi.detail(id!),
    enabled: !!id,
  });
}

export function useDeliveries(id: string | null) {
  return useQuery({
    queryKey: inboxKeys.deliveries(id ?? '__none__'),
    queryFn: () => messageInboxApi.deliveries(id!),
    enabled: !!id,
  });
}

/** 铃铛未读数：每 60s 轮询。 */
export function useUnreadCount() {
  return useQuery({
    queryKey: inboxKeys.unread(),
    queryFn: () => messageInboxApi.unreadCount(),
    refetchInterval: 60_000,
    refetchOnWindowFocus: true,
  });
}

function useInvalidateAll() {
  const qc = useQueryClient();
  return () => qc.invalidateQueries({ queryKey: inboxKeys.all });
}

export function useMarkRead() {
  const invalidate = useInvalidateAll();
  return useMutation({
    mutationFn: (id: string) => messageInboxApi.markRead(id),
    onSuccess: invalidate,
  });
}

export function useMarkAllRead() {
  const invalidate = useInvalidateAll();
  return useMutation({
    mutationFn: (category?: MessageCategory | null) => messageInboxApi.markAllRead(category),
    onSuccess: invalidate,
  });
}

export function useDeleteMessage() {
  const invalidate = useInvalidateAll();
  return useMutation({
    mutationFn: (id: string) => messageInboxApi.remove(id),
    onSuccess: invalidate,
  });
}
