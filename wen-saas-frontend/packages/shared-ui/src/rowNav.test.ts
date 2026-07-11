import { afterEach, describe, expect, it, vi } from 'vitest';
import { shouldSkipRowNav } from './rowNav';

afterEach(() => vi.restoreAllMocks());

function mockSelection(s: Partial<Selection> | null) {
  vi.spyOn(window, 'getSelection').mockReturnValue(s as Selection | null);
}

describe('shouldSkipRowNav', () => {
  it('无选区（光标折叠）时返回 false，照常导航', () => {
    mockSelection({ isCollapsed: true, toString: () => '' });
    expect(shouldSkipRowNav()).toBe(false);
  });

  it('存在非空文本选区时返回 true，跳过导航', () => {
    mockSelection({ isCollapsed: false, toString: () => 'TENANT-001' });
    expect(shouldSkipRowNav()).toBe(true);
  });

  it('选区只含空白时返回 false（不拦正常点击）', () => {
    mockSelection({ isCollapsed: false, toString: () => '   ' });
    expect(shouldSkipRowNav()).toBe(false);
  });

  it('getSelection 返回 null 时返回 false', () => {
    mockSelection(null);
    expect(shouldSkipRowNav()).toBe(false);
  });
});
