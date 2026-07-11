import { useInfiniteQuery, useQuery } from '@tanstack/react-query';
import { crmApi } from '../../lib/api';

const PAGE_SIZE = 20;

/**
 * 我的客户（/mine 端点，负责人恒为当前用户，权限 crm_demo.customer.view）；status 为空 = 全部阶段。
 * 分页无限加载（上拉加载更多），total 为雪花字符串，Number 后判断是否还有下一页。
 */
export function useMyCustomersInfinite(status: string | undefined) {
  return useInfiniteQuery({
    queryKey: ['crm-demo', 'my-customers', status ?? 'all'],
    queryFn: ({ pageParam }) =>
      crmApi.listMine({
        ...(status ? { status } : {}),
        page: pageParam,
        size: PAGE_SIZE,
      }),
    initialPageParam: 1,
    getNextPageParam: (lastPage, pages) => {
      const loaded = pages.reduce((n, p) => n + p.records.length, 0);
      return loaded < Number(lastPage.total) ? pages.length + 1 : undefined;
    },
  });
}

/** 客户详情（列表行点开的简单详情弹层用）。 */
export function useCustomerDetail(id: string | undefined) {
  return useQuery({
    queryKey: ['crm-demo', 'customer-detail', id],
    queryFn: () => crmApi.get(id!),
    enabled: !!id,
  });
}
