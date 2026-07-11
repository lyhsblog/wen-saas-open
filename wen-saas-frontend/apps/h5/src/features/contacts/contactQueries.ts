import { useInfiniteQuery, useQuery } from '@tanstack/react-query';
import { deptApi, memberApi } from '../../lib/api';

const PAGE_SIZE = 50;

export function useDeptTree() {
  return useQuery({ queryKey: ['contacts', 'deptTree'], queryFn: () => deptApi.listTree() });
}

/** 成员列表：分页无限加载（上拉加载更多），按 total 判断是否还有下一页。 */
export function useMembersInfinite(deptId: string | undefined, keyword: string) {
  return useInfiniteQuery({
    queryKey: ['contacts', 'members', deptId ?? 'all', keyword],
    queryFn: ({ pageParam }) =>
      memberApi.list({
        ...(deptId ? { deptId, includeSubDepts: true } : {}),
        ...(keyword ? { keyword } : {}),
        status: 1,
        page: pageParam,
        pageSize: PAGE_SIZE,
      }),
    initialPageParam: 1,
    getNextPageParam: (lastPage, pages) => {
      const loaded = pages.reduce((n, p) => n + p.items.length, 0);
      return loaded < Number(lastPage.total) ? pages.length + 1 : undefined;
    },
  });
}

export function useMemberDetail(userId: string | undefined) {
  return useQuery({
    queryKey: ['contacts', 'detail', userId],
    queryFn: () => memberApi.detail(userId!),
    enabled: !!userId,
  });
}
