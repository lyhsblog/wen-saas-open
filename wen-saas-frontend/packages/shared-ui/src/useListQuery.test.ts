import { describe, expect, it } from 'vitest';
import { act, renderHook } from '@testing-library/react';
import { useListQuery } from './useListQuery';

type Filters = { keyword: string; status?: 'ON' | 'OFF' };

describe('useListQuery', () => {
  it('初始状态：page=initialPage，query 携带 page/size 并去掉空筛选', () => {
    const { result } = renderHook(() => useListQuery<Filters>({ keyword: '' }, { size: 10 }));
    expect(result.current.page).toBe(1);
    expect(result.current.size).toBe(10);
    // keyword='' 被去掉，status 未设置
    expect(result.current.query).toEqual({ page: 1, size: 10 });
  });

  it('setFilter 改筛选并自动复位到首页', () => {
    const { result } = renderHook(() => useListQuery<Filters>({ keyword: '' }, { size: 10 }));
    act(() => result.current.setPage(3));
    expect(result.current.page).toBe(3);

    act(() => result.current.setFilter('keyword', 'abc'));
    expect(result.current.page).toBe(1); // 自动复位
    expect(result.current.filters.keyword).toBe('abc');
    expect(result.current.query).toEqual({ keyword: 'abc', page: 1, size: 10 });
  });

  it('setPage 只翻页、不动筛选、不复位', () => {
    const { result } = renderHook(() => useListQuery<Filters>({ keyword: 'x' }));
    act(() => result.current.setPage(5));
    expect(result.current.page).toBe(5);
    expect(result.current.filters.keyword).toBe('x');
  });

  it('patchFilters 批量改并复位首页', () => {
    const { result } = renderHook(() => useListQuery<Filters>({ keyword: '' }, { size: 10 }));
    act(() => result.current.setPage(4));
    act(() => result.current.patchFilters({ keyword: 'k', status: 'ON' }));
    expect(result.current.page).toBe(1);
    expect(result.current.query).toEqual({ keyword: 'k', status: 'ON', page: 1, size: 10 });
  });

  it('resetFilters 还原到初始筛选并回到首页', () => {
    const { result } = renderHook(() => useListQuery<Filters>({ keyword: '' }));
    act(() => {
      result.current.setFilter('keyword', 'k');
      result.current.setPage(2);
    });
    act(() => result.current.resetFilters());
    expect(result.current.page).toBe(1);
    expect(result.current.filters).toEqual({ keyword: '' });
  });

  it('initialPage 自定义时，复位回该页而非 1', () => {
    const { result } = renderHook(() => useListQuery<Filters>({ keyword: '' }, { initialPage: 0 }));
    act(() => result.current.setPage(5));
    act(() => result.current.setFilter('keyword', 'q'));
    expect(result.current.page).toBe(0);
  });
});
