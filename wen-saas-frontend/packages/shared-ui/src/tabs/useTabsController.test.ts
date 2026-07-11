import { describe, expect, it } from 'vitest';
import { act, renderHook } from '@testing-library/react';
import { closableByLru, nextActiveAfterClose, useTabsController } from './useTabsController';
import type { TabItem } from './types';

const T = (key: string, closable = true): TabItem => ({ key, title: key, closable });

describe('nextActiveAfterClose', () => {
  it('活跃页签仍在保留集合内时维持不变', () => {
    const prev = [T('a'), T('b'), T('c')];
    const remaining = [T('a'), T('c')];
    expect(nextActiveAfterClose(prev, remaining, 'a')).toBe('a');
  });

  it('关掉活跃页签时优先取右侧最近的保留项', () => {
    const prev = [T('a'), T('b'), T('c')];
    const remaining = [T('a'), T('c')];
    expect(nextActiveAfterClose(prev, remaining, 'b')).toBe('c');
  });

  it('右侧无保留项时回退到左侧最近的', () => {
    const prev = [T('a'), T('b'), T('c')];
    const remaining = [T('a'), T('b')];
    expect(nextActiveAfterClose(prev, remaining, 'c')).toBe('b');
  });

  it('无任何保留项时返回 null', () => {
    const prev = [T('a')];
    expect(nextActiveAfterClose(prev, [], 'a')).toBeNull();
  });
});

describe('closableByLru', () => {
  it('排除不可关闭项并按最久未访问优先排序', () => {
    const tabs: TabItem[] = [
      { key: '/home', title: 'H', closable: false, lastActiveAt: 100 },
      { key: '/a', title: 'A', lastActiveAt: 300 },
      { key: '/b', title: 'B', lastActiveAt: 100 },
      { key: '/c', title: 'C' }, // 无 lastActiveAt 视为最久
    ];
    expect(closableByLru(tabs).map((t) => t.key)).toEqual(['/c', '/b', '/a']);
  });
});

describe('useTabsController', () => {
  it('open 幂等：新增追加、重复仅刷新标题、不重复入列', () => {
    const { result } = renderHook(() => useTabsController([T('/workbench', false)]));
    act(() => result.current.open({ key: '/a', title: 'A' }));
    act(() => result.current.open({ key: '/a', title: 'A2' }));
    expect(result.current.tabs.map((t) => t.key)).toEqual(['/workbench', '/a']);
    expect(result.current.tabs[1]!.title).toBe('A2');
  });

  it('close 跳过不可关闭页签', () => {
    const { result } = renderHook(() => useTabsController([T('/workbench', false)]));
    let next: string | null = null;
    act(() => {
      next = result.current.close('/workbench', '/workbench');
    });
    expect(result.current.tabs).toHaveLength(1);
    expect(next).toBe('/workbench');
  });

  it('close 关掉活跃页签返回应激活的相邻 key', () => {
    const { result } = renderHook(() => useTabsController([T('/workbench', false)]));
    act(() => result.current.open({ key: '/a', title: 'A' }));
    act(() => result.current.open({ key: '/b', title: 'B' }));
    let next: string | null = null;
    act(() => {
      next = result.current.close('/a', '/a');
    });
    expect(result.current.tabs.map((t) => t.key)).toEqual(['/workbench', '/b']);
    expect(next).toBe('/b');
  });

  it('closeOthers 保留目标与不可关闭页签', () => {
    const { result } = renderHook(() => useTabsController([T('/workbench', false)]));
    act(() => result.current.open({ key: '/a', title: 'A' }));
    act(() => result.current.open({ key: '/b', title: 'B' }));
    act(() => {
      result.current.closeOthers('/a');
    });
    expect(result.current.tabs.map((t) => t.key)).toEqual(['/workbench', '/a']);
  });

  it('closeRight 只关右侧可关闭页签', () => {
    const { result } = renderHook(() => useTabsController([T('/workbench', false)]));
    act(() => result.current.open({ key: '/a', title: 'A' }));
    act(() => result.current.open({ key: '/b', title: 'B' }));
    act(() => result.current.open({ key: '/c', title: 'C' }));
    act(() => {
      result.current.closeRight('/a', '/c');
    });
    expect(result.current.tabs.map((t) => t.key)).toEqual(['/workbench', '/a']);
  });

  it('closeAll 仅保留不可关闭页签并返回其 key', () => {
    const { result } = renderHook(() => useTabsController([T('/workbench', false)]));
    act(() => result.current.open({ key: '/a', title: 'A' }));
    let next: string | null = null;
    act(() => {
      next = result.current.closeAll('/a');
    });
    expect(result.current.tabs.map((t) => t.key)).toEqual(['/workbench']);
    expect(next).toBe('/workbench');
  });

  it('open 写入 lastActiveAt，再次 open 同 key 会刷新时间且保留 dirty', () => {
    const { result } = renderHook(() => useTabsController());
    act(() => result.current.open({ key: '/a', title: 'A' }));
    const first = result.current.tabs[0]!.lastActiveAt!;
    act(() => result.current.setDirty('/a', true));
    act(() => result.current.open({ key: '/a', title: 'A 改' }));
    const tab = result.current.tabs[0]!;
    expect(tab.title).toBe('A 改');
    expect(tab.dirty).toBe(true); // open 不清除 dirty
    expect(tab.lastActiveAt!).toBeGreaterThanOrEqual(first);
  });

  it('setDirty 设置 / 清除脏标记', () => {
    const { result } = renderHook(() => useTabsController([T('/a')]));
    act(() => result.current.setDirty('/a', true));
    expect(result.current.tabs[0]!.dirty).toBe(true);
    act(() => result.current.setDirty('/a', false));
    expect(result.current.tabs[0]!.dirty).toBe(false);
  });
});
