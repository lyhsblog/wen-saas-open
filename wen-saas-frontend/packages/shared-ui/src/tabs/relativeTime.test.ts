import { describe, expect, it } from 'vitest';
import { formatRelativeFromNow } from './relativeTime';

describe('formatRelativeFromNow', () => {
  const now = 1_000_000_000_000;
  it('缺省 / 未来 / 10 秒内 → 刚刚', () => {
    expect(formatRelativeFromNow(undefined, now)).toBe('刚刚');
    expect(formatRelativeFromNow(now + 5_000, now)).toBe('刚刚');
    expect(formatRelativeFromNow(now - 9_999, now)).toBe('刚刚');
  });
  it('10 秒 ~ 1 分钟 → N 秒前', () => {
    expect(formatRelativeFromNow(now - 10_000, now)).toBe('10 秒前');
    expect(formatRelativeFromNow(now - 30_000, now)).toBe('30 秒前');
    expect(formatRelativeFromNow(now - 59_999, now)).toBe('59 秒前');
  });
  it('分钟 / 小时 / 天 粒度', () => {
    expect(formatRelativeFromNow(now - 5 * 60_000, now)).toBe('5 分钟前');
    expect(formatRelativeFromNow(now - 3 * 3_600_000, now)).toBe('3 小时前');
    expect(formatRelativeFromNow(now - 2 * 86_400_000, now)).toBe('2 天前');
  });
});
