import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type {
  MyTicketsQuery,
  SubmitTicketRequest,
  TenantAllTicketsQuery,
} from '@wen-saas/api-client';
import { ticketApi } from './ticketApi';

export const ticketKeys = {
  all: ['ticket'] as const,
  myList: (q: MyTicketsQuery) => [...ticketKeys.all, 'my', q] as const,
  allList: (q: TenantAllTicketsQuery) => [...ticketKeys.all, 'all', q] as const,
  detail: (id: string) => [...ticketKeys.all, 'detail', id] as const,
};

export function useMyTickets(query: MyTicketsQuery) {
  return useQuery({
    queryKey: ticketKeys.myList(query),
    queryFn: () => ticketApi.myTickets(query),
  });
}

export function useAllTickets(query: TenantAllTicketsQuery, enabled: boolean) {
  return useQuery({
    queryKey: ticketKeys.allList(query),
    queryFn: () => ticketApi.allTickets(query),
    enabled,
  });
}

export function useTicketDetail(id: string | null) {
  return useQuery({
    queryKey: id ? ticketKeys.detail(id) : ticketKeys.detail('__none__'),
    queryFn: () => ticketApi.getDetail(id!),
    enabled: !!id,
  });
}

export function useSubmitTicket() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: SubmitTicketRequest) => ticketApi.submit(body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ticketKeys.all }),
  });
}

export function useReplyTicket(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (content: string) => ticketApi.reply(id, content),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ticketKeys.detail(id) });
      void qc.invalidateQueries({ queryKey: ticketKeys.all });
    },
  });
}

export function useConfirmTicket(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => ticketApi.confirm(id),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ticketKeys.detail(id) });
      void qc.invalidateQueries({ queryKey: ticketKeys.all });
    },
  });
}

export function useReopenTicket(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => ticketApi.reopen(id),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ticketKeys.detail(id) });
      void qc.invalidateQueries({ queryKey: ticketKeys.all });
    },
  });
}

export function useUploadTicketAttachment(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (file: File) => ticketApi.addAttachment(id, file),
    onSuccess: () => qc.invalidateQueries({ queryKey: ticketKeys.detail(id) }),
  });
}
