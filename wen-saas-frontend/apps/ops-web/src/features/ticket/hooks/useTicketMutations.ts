import { App } from 'antd';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import {
  opsTicketApi,
  describeApiError,
  type TicketMessageView,
  type TicketPriority,
} from '@wen-saas/api-client';
import { http } from '../../../lib/http';

/** 列表 + 详情失效。工单状态变更影响总数/排序，列表整体失效。 */
function useInvalidate() {
  const qc = useQueryClient();
  return (id?: string) => {
    void qc.invalidateQueries({ queryKey: ['ops', 'tickets', 'list'] });
    if (id != null) void qc.invalidateQueries({ queryKey: ['ops', 'ticket', id] });
  };
}

export function useClaimTicket() {
  const { message } = App.useApp();
  const invalidate = useInvalidate();
  return useMutation<void, Error, string>({
    mutationFn: (id) => opsTicketApi(http).claim(id),
    onSuccess: (_d, id) => {
      void message.success('已认领');
      invalidate(id);
    },
    onError: (err) => void message.error(describeApiError(err, '认领失败')),
  });
}

export function useTransferTicket(id: string) {
  const { message } = App.useApp();
  const invalidate = useInvalidate();
  return useMutation<void, Error, string>({
    mutationFn: (assigneeId) => opsTicketApi(http).transfer(id, assigneeId),
    onSuccess: () => {
      void message.success('已转单');
      invalidate(id);
    },
    onError: (err) => void message.error(describeApiError(err, '转单失败')),
  });
}

export function useChangePriority(id: string) {
  const { message } = App.useApp();
  const invalidate = useInvalidate();
  return useMutation<void, Error, TicketPriority>({
    mutationFn: (priority) => opsTicketApi(http).changePriority(id, priority),
    onSuccess: () => {
      void message.success('优先级已调整');
      invalidate(id);
    },
    onError: (err) => void message.error(describeApiError(err, '调整失败')),
  });
}

export function useReplyTicket(id: string) {
  const { message } = App.useApp();
  const invalidate = useInvalidate();
  return useMutation<TicketMessageView, Error, string>({
    mutationFn: (content) => opsTicketApi(http).reply(id, content),
    onSuccess: () => {
      void message.success('已回复');
      invalidate(id);
    },
    onError: (err) => void message.error(describeApiError(err, '回复失败')),
  });
}

export function useResolveTicket() {
  const { message } = App.useApp();
  const invalidate = useInvalidate();
  return useMutation<void, Error, string>({
    mutationFn: (id) => opsTicketApi(http).resolve(id),
    onSuccess: (_d, id) => {
      void message.success('已标记解决');
      invalidate(id);
    },
    onError: (err) => void message.error(describeApiError(err, '操作失败')),
  });
}

export function useCloseTicket(id: string) {
  const { message } = App.useApp();
  const invalidate = useInvalidate();
  return useMutation<void, Error, string>({
    mutationFn: (reason) => opsTicketApi(http).close(id, reason),
    onSuccess: () => {
      void message.success('工单已关闭');
      invalidate(id);
    },
    onError: (err) => void message.error(describeApiError(err, '关闭失败')),
  });
}

export function useReopenTicket() {
  const { message } = App.useApp();
  const invalidate = useInvalidate();
  return useMutation<void, Error, string>({
    mutationFn: (id) => opsTicketApi(http).reopen(id),
    onSuccess: (_d, id) => {
      void message.success('工单已重启');
      invalidate(id);
    },
    onError: (err) => void message.error(describeApiError(err, '重启失败')),
  });
}

export function useUploadAttachment(id: string) {
  const { message } = App.useApp();
  const invalidate = useInvalidate();
  return useMutation<void, Error, File>({
    mutationFn: async (file) => {
      await opsTicketApi(http).addAttachment(id, file);
    },
    onSuccess: () => {
      void message.success('附件已上传');
      invalidate(id);
    },
    onError: (err) => void message.error(describeApiError(err, '上传失败')),
  });
}
