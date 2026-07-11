import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { InboxQuery, MessageCategory } from '@wen-saas/api-client';
import { inboxApi } from '../../lib/api';

const PAGE_SIZE = 20;

/** 收件箱：分页无限加载（上拉加载更多），按 total 判断是否还有下一页。 */
export function useInboxInfinite(query: Omit<InboxQuery, 'page' | 'size'>) {
  return useInfiniteQuery({
    queryKey: ['inbox', 'list', query],
    queryFn: ({ pageParam }) => inboxApi.list({ ...query, page: pageParam, size: PAGE_SIZE }),
    initialPageParam: 1,
    getNextPageParam: (lastPage, pages) => {
      const loaded = pages.reduce((n, p) => n + p.records.length, 0);
      return loaded < Number(lastPage.total) ? pages.length + 1 : undefined;
    },
  });
}

export function useUnreadSummary() {
  return useQuery({
    queryKey: ['inbox', 'unread'],
    queryFn: () => inboxApi.unreadCount(),
    // tab 角标：进入前台时刷新
    refetchOnWindowFocus: true,
  });
}

export function useMessageDetail(id: string | undefined) {
  return useQuery({
    queryKey: ['inbox', 'detail', id],
    queryFn: () => inboxApi.detail(id!),
    enabled: !!id,
  });
}

export function useMarkRead() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => inboxApi.markRead(id),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['inbox'] });
    },
  });
}

/** 全部已读：传 category 只清该分类，null/缺省清全部未读。 */
export function useMarkAllRead() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (category?: MessageCategory | null) => inboxApi.markAllRead(category),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['inbox'] });
    },
  });
}
