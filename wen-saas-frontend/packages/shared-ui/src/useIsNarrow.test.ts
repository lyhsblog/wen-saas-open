import { afterEach, describe, expect, it, vi } from 'vitest';
import { act, renderHook } from '@testing-library/react';
import { NARROW_MEDIA_QUERY, useIsNarrow } from './useIsNarrow';

type Listener = () => void;

/** 可控 matchMedia：手动翻转 matches 并触发 change 监听。 */
function installMatchMedia(initialMatches: boolean) {
  const listeners = new Set<Listener>();
  const mql = {
    matches: initialMatches,
    media: NARROW_MEDIA_QUERY,
    onchange: null,
    addListener: () => undefined,
    removeListener: () => undefined,
    addEventListener: (_: string, cb: Listener) => listeners.add(cb),
    removeEventListener: (_: string, cb: Listener) => listeners.delete(cb),
    dispatchEvent: () => false,
  };
  const spy = vi
    .spyOn(window, 'matchMedia')
    .mockImplementation(() => mql as unknown as MediaQueryList);
  return {
    spy,
    setMatches(next: boolean) {
      mql.matches = next;
      for (const cb of listeners) cb();
    },
    listenerCount: () => listeners.size,
  };
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('useIsNarrow', () => {
  it('断点常量固定 1100px（全站唯一来源）', () => {
    expect(NARROW_MEDIA_QUERY).toBe('(width < 1100px)');
  });

  it('初始值取 matchMedia.matches，change 时跟随更新', () => {
    const mm = installMatchMedia(false);
    const { result } = renderHook(() => useIsNarrow());
    expect(result.current).toBe(false);

    act(() => mm.setMatches(true));
    expect(result.current).toBe(true);

    act(() => mm.setMatches(false));
    expect(result.current).toBe(false);
  });

  it('卸载时移除 change 监听', () => {
    const mm = installMatchMedia(true);
    const { result, unmount } = renderHook(() => useIsNarrow());
    expect(result.current).toBe(true);
    expect(mm.listenerCount()).toBe(1);
    unmount();
    expect(mm.listenerCount()).toBe(0);
  });
});
