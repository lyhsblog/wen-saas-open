import { useInfiniteQuery, useQuery } from '@tanstack/react-query';
import type { FileSpace } from '@wen-saas/api-client';
import { fileApi } from '../../lib/api';

const PAGE_SIZE = 50;

/**
 * 文件列表：分页无限加载。后端语义：folders / breadcrumb 每页全量返回（不分页），
 * 仅 files 按 page/size 分页、total 为文件总数——展示层取第一页的 folders/breadcrumb，
 * files 跨页拼接。
 */
export function useFileListInfinite(space: FileSpace, folderId: string | null) {
  return useInfiniteQuery({
    queryKey: ['files', 'list', space, folderId ?? 'root'],
    queryFn: ({ pageParam }) => fileApi.list(space, { folderId, page: pageParam, size: PAGE_SIZE }),
    initialPageParam: 1,
    getNextPageParam: (lastPage, pages) => {
      const loaded = pages.reduce((n, p) => n + p.files.length, 0);
      return loaded < Number(lastPage.total) ? pages.length + 1 : undefined;
    },
  });
}

export function useQuota() {
  return useQuery({ queryKey: ['files', 'quota'], queryFn: () => fileApi.quota() });
}
