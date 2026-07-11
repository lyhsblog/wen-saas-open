import { useCallback, useMemo, useRef, useState } from 'react';

/**
 * 列表页查询状态：管理「筛选条件 + 当前页」，并在任一筛选变更时自动把页码复位到第 1 页
 * （消除各 ListPane 反复手写 `setXxx(v); resetPage()` 且容易漏写 resetPage 的样板）。
 *
 * 设计取舍：本 hook 只管状态，不依赖 @tanstack/react-query —— useQuery 仍由页面持有，
 * 因为 queryKey/queryFn 因页而异。页面拿到 `query`（或 `filters`）后自行调用 api.list。
 *
 * 用法分两种：
 *  1. 筛选字段名与后端入参一一对应（多数列表）：直接把 `query` 喂给 queryKey 与 api.list。
 *  2. 存在「哨兵值/字段改名」（如 tab='all' 表示不过滤、tab→status）：用 `filters` 自行拼装
 *     后端 query，分页仍交给本 hook 的 `page` / `setPage`。
 */
export type ListQuery<F> = F & { page: number; size: number };

export type UseListQueryResult<F extends Record<string, unknown>> = {
  /** 当前筛选条件（含哨兵原值，未做去空）。 */
  filters: F;
  page: number;
  size: number;
  /** 仅翻页，不动筛选；接给 antd Table pagination.onChange。 */
  setPage: (page: number) => void;
  /** 改单个筛选字段，自动复位到首页。 */
  setFilter: <K extends keyof F>(key: K, value: F[K]) => void;
  /** 批量改多个筛选字段，自动复位到首页。 */
  patchFilters: (patch: Partial<F>) => void;
  /** 还原到初始筛选并回到首页。 */
  resetFilters: () => void;
  /**
   * 便捷查询对象：filters 去掉 undefined / null / '' 后，附带 page + size。
   * 适用于「字段名与后端一致、空值即不过滤」的常规列表，可同时用于 queryKey 与 api.list(query)。
   */
  query: ListQuery<F>;
};

export type UseListQueryOptions = {
  /** 每页条数，默认 20。 */
  size?: number;
  /** 起始页（也是筛选变更后复位到的页），默认 1。 */
  initialPage?: number;
};

function pruneEmpty<F extends Record<string, unknown>>(filters: F): F {
  const out: Record<string, unknown> = {};
  for (const [k, v] of Object.entries(filters)) {
    if (v === undefined || v === null || v === '') continue;
    out[k] = v;
  }
  return out as F;
}

export function useListQuery<F extends Record<string, unknown>>(
  initialFilters: F,
  options?: UseListQueryOptions,
): UseListQueryResult<F> {
  const size = options?.size ?? 20;
  const initialPage = options?.initialPage ?? 1;

  // 锁定初始筛选，使 resetFilters 稳定且复位到真正的初始值（即便调用方每次渲染传入新字面量）。
  // 仅在事件回调 resetFilters 内读取 .current，不在渲染期读取。
  const initialRef = useRef(initialFilters);

  const [filters, setFilters] = useState<F>(initialFilters);
  const [page, setPage] = useState(initialPage);

  const setFilter = useCallback(
    <K extends keyof F>(key: K, value: F[K]) => {
      setFilters((prev) => ({ ...prev, [key]: value }));
      setPage(initialPage);
    },
    [initialPage],
  );

  const patchFilters = useCallback(
    (patch: Partial<F>) => {
      setFilters((prev) => ({ ...prev, ...patch }));
      setPage(initialPage);
    },
    [initialPage],
  );

  const resetFilters = useCallback(() => {
    setFilters(initialRef.current);
    setPage(initialPage);
  }, [initialPage]);

  const query = useMemo<ListQuery<F>>(
    () => ({ ...pruneEmpty(filters), page, size }),
    [filters, page, size],
  );

  return { filters, page, size, setPage, setFilter, patchFilters, resetFilters, query };
}
