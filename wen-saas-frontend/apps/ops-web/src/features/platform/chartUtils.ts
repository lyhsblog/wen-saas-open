/** 平台监控看板图表的颜色常量与工具（与 charts.tsx 组件分离，满足 react-refresh 单一导出）。 */

/**
 * 图表语义色，统一引用 design-system CSS 变量（单一真源、随主题生效）。
 * 仅用于 SVG stroke/fill 与内联 CSS，均可解析 var()；不要在 JS 侧对这些值做
 * 颜色插值 / 拼 rgba() 等计算（var() 字符串不可解析）。
 */
export const CHART_COLORS = {
  brand: 'var(--brand)',
  success: 'var(--success)',
  danger: 'var(--danger)',
  warning: 'var(--warning)',
  tertiary: 'var(--text-tertiary)',
  purple: 'var(--purple)',
  cyan: 'var(--cyan)',
};

/** 套餐分布配色循环（分类色，复用 CHART_COLORS，品红补位取自 tokens 辅助色）。 */
export const PLAN_PALETTE = [
  CHART_COLORS.brand,
  CHART_COLORS.cyan,
  CHART_COLORS.purple,
  CHART_COLORS.warning,
  CHART_COLORS.tertiary,
  CHART_COLORS.success,
  'var(--magenta)',
];

export type DonutItem = { name: string; count: number; color: string };

/**
 * 趋势图 Y 轴刻度规整：返回一个「好看」的上界与刻度段数，保证整数取整后刻度不重复
 * （修 O9-01：小数据如 max=2 时原先 5 条刻度取整出现 2,2 / 1,1,1 的重复标签）。
 */
export function niceYScale(rawMax: number, maxTicks = 4): { niceMax: number; tickCount: number } {
  if (rawMax <= maxTicks) {
    // 小整数区间：步长固定为 1，刻度数=上界，标签天然唯一（0,1,2,…）
    const m = Math.max(1, Math.ceil(rawMax));
    return { niceMax: m, tickCount: m };
  }
  const pow = Math.pow(10, Math.floor(Math.log10(rawMax)));
  for (const c of [1, 2, 2.5, 5, 10]) {
    if (c * pow >= rawMax) {
      return { niceMax: c * pow, tickCount: maxTicks };
    }
  }
  return { niceMax: rawMax, tickCount: maxTicks };
}

/** 字节数 → 可读体积：全站统一实现，此处转发供图表侧引用。 */
export { formatBytes } from '@wen-saas/shared-ui';
