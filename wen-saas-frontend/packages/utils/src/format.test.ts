import { describe, expect, it } from 'vitest';
import { fileExt, formatBytes } from './format';

describe('formatBytes', () => {
  it('空值 / NaN 显示「—」', () => {
    expect(formatBytes(null)).toBe('—');
    expect(formatBytes(undefined)).toBe('—');
    expect(formatBytes(Number.NaN)).toBe('—');
  });

  it('小于 1024 直接按 B 展示', () => {
    expect(formatBytes(0)).toBe('0 B');
    expect(formatBytes(1023)).toBe('1023 B');
  });

  it('KB 起一位小数，整数值 / ≥100 去小数', () => {
    expect(formatBytes(1536)).toBe('1.5 KB');
    expect(formatBytes(1024)).toBe('1 KB');
    expect(formatBytes(100 * 1024)).toBe('100 KB');
    expect(formatBytes(2.5 * 1024 * 1024)).toBe('2.5 MB');
  });

  it('逐级进位，上限 TB', () => {
    expect(formatBytes(1024 ** 3)).toBe('1 GB');
    expect(formatBytes(1024 ** 4)).toBe('1 TB');
    expect(formatBytes(2048 * 1024 ** 4)).toBe('2048 TB');
  });
});

describe('fileExt', () => {
  it('取最后一个「.」后的扩展名并转大写', () => {
    expect(fileExt('报表.pdf')).toBe('PDF');
    expect(fileExt('archive.tar.gz')).toBe('GZ');
  });

  it('无扩展名 / 隐藏文件回退 FILE', () => {
    expect(fileExt('README')).toBe('FILE');
    expect(fileExt('.gitignore')).toBe('FILE');
  });
});
