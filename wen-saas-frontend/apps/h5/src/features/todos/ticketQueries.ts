import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { SubmitTicketRequest, TicketStatus } from '@wen-saas/api-client';
import { ticketApi } from '../../lib/api';

const PAGE_SIZE = 20;

/** 我的工单：分页无限加载（上拉加载更多），按 total 判断是否还有下一页。 */
export function useMyTicketsInfinite(status?: TicketStatus) {
  return useInfiniteQuery({
    queryKey: ['tickets', 'mine', status ?? 'ALL'],
    queryFn: ({ pageParam }) => ticketApi.myTickets({ status, page: pageParam, size: PAGE_SIZE }),
    initialPageParam: 1,
    getNextPageParam: (lastPage, pages) => {
      const loaded = pages.reduce((n, p) => n + p.records.length, 0);
      return loaded < Number(lastPage.total) ? pages.length + 1 : undefined;
    },
  });
}

export function useTicketDetail(ticketId: string | undefined) {
  return useQuery({
    queryKey: ['tickets', 'detail', ticketId],
    queryFn: () => ticketApi.getDetail(ticketId!),
    enabled: !!ticketId,
  });
}

export function useReplyTicket(ticketId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (content: string) => ticketApi.reply(ticketId, content),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['tickets', 'detail', ticketId] });
      void qc.invalidateQueries({ queryKey: ['tickets', 'mine'] });
    },
  });
}

export function useConfirmTicket(ticketId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => ticketApi.confirm(ticketId),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['tickets'] });
    },
  });
}

export function useReopenTicket(ticketId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => ticketApi.reopen(ticketId),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['tickets'] });
    },
  });
}

export function useSubmitTicket() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: SubmitTicketRequest) => ticketApi.submit(body),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['tickets', 'mine'] });
    },
  });
}
