/** 便签色板：key → { 背景, 边框 }。服务端只做 key 白名单，颜色值前端定义。 */

export type StickyTone = { bg: string; border: string };

export const DEFAULT_STICKY_COLOR = 'yellow';

const DEFAULT_TONE: StickyTone = { bg: 'oklch(0.95 0.06 95)', border: 'oklch(0.82 0.09 95)' };

export const STICKY_COLORS: Record<string, StickyTone> = {
  yellow: DEFAULT_TONE,
  green: { bg: 'oklch(0.95 0.05 150)', border: 'oklch(0.82 0.08 150)' },
  blue: { bg: 'oklch(0.95 0.04 240)', border: 'oklch(0.82 0.07 240)' },
  pink: { bg: 'oklch(0.94 0.05 350)', border: 'oklch(0.82 0.08 350)' },
  purple: { bg: 'oklch(0.94 0.05 300)', border: 'oklch(0.82 0.08 300)' },
};

/** 取某 color key 的色值，未知 key 回退默认。 */
export function stickyTone(color: string): StickyTone {
  return STICKY_COLORS[color] ?? DEFAULT_TONE;
}
