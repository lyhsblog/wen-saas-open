export type Density = 'compact' | 'standard' | 'loose';

/**
 * 密度当前没有用户可见开关，本文件与 tokens.css 的 data-density 变体为预留能力。
 * 若未来上线开关，两条硬约束：
 *
 * 1. 只进 PC 两端（ops-web / tenant-web），h5 永远锁 standard——compact 的
 *    --density-row: 36px 低于 44px 最小触控目标。applyDensity 不进 h5 bundle、
 *    h5 样式不引密度变体，即天然隔离。
 * 2. AntD 组件不读 CSS 变量：data-density 变体只影响 wa-* 布局类（wa-card 等），
 *    AntD Table/Button 的尺寸走 ConfigProvider componentSize。上开关必须
 *    applyDensity 与 componentSize 双链路同调（compact→'small'、standard→'middle'、
 *    loose→'large'），只改一侧会出现「wa 卡片缩了 AntD 表格没缩」的割裂。
 */

/**
 * 把 density 写到 `<html data-density="...">`，触发 `tokens.css` 中的密度变体。
 * standard 模式删除属性以回落到默认 tokens。
 */
export function applyDensity(density: Density): void {
  if (typeof document === 'undefined') return;
  if (density === 'standard') {
    document.documentElement.removeAttribute('data-density');
  } else {
    document.documentElement.setAttribute('data-density', density);
  }
}
