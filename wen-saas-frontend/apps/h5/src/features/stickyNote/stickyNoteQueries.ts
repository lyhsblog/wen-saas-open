import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { StickyNote } from '@wen-saas/api-client';
import { stickyNoteApi } from '../../lib/api';

/**
 * 复用 PC 同源 /api/v1/base/sticky-notes；列表按 updatedAt 倒序（服务端）。
 *
 * 增改删只在缓存内原位更新、不 invalidate：会话内展示顺序冻结——编辑当下不冒顶、不跳位。
 * 重新进入页面（refetchOnMount:'always'）或下拉刷新时才 refetch，届时按 updatedAt 重排。
 */
export const stickyKeys = {
  all: ['sticky-notes'] as const,
};

export function useStickyNotes() {
  return useQuery({
    queryKey: stickyKeys.all,
    queryFn: () => stickyNoteApi.list(),
    // 进页面即视作「重新进入」：总是拉最新并按 updatedAt 重排（会话内的原位编辑到此归位）
    refetchOnMount: 'always',
  });
}

export function useCreateStickyNote() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (v: { content: string; color: string }) => stickyNoteApi.create(v.content, v.color),
    // 新建置顶（updatedAt 最新）
    onSuccess: (created) =>
      qc.setQueryData<StickyNote[]>(stickyKeys.all, (list) => [created, ...(list ?? [])]),
  });
}

export function useUpdateStickyNote() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (v: { id: string; content: string; color: string }) =>
      stickyNoteApi.update(v.id, v.content, v.color),
    // 原位替换，保持列表顺序不变（编辑不冒顶）；重排留待下次进入/刷新
    onSuccess: (updated) =>
      qc.setQueryData<StickyNote[]>(stickyKeys.all, (list) =>
        (list ?? []).map((n) => (n.id === updated.id ? updated : n)),
      ),
  });
}

export function useDeleteStickyNote() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => stickyNoteApi.remove(id),
    onSuccess: (_r, id) =>
      qc.setQueryData<StickyNote[]>(stickyKeys.all, (list) =>
        (list ?? []).filter((n) => n.id !== id),
      ),
  });
}
