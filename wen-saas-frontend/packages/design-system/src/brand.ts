export type Brand = 'blue' | 'indigo' | 'teal' | 'slate';

/** 品牌色板顺序（取色器等 UI 按此展示）。 */
export const BRAND_KEYS: Brand[] = ['blue', 'indigo', 'teal', 'slate'];

/**
 * 把品牌色写到 `<html data-brand="...">`，触发 `tokens.css` 中的品牌变体
 * （驱动 .wa-* 类与所有直接消费 --brand* CSS 变量的样式）。blue 为默认品牌，
 * 删除属性以回落 :root 默认 tokens。
 *
 * 注意：AntD 组件不读 CSS 变量，运行时切换需同时用 `buildTenantTheme(brand)`
 * 更新 ConfigProvider 的 theme，两条链路都走全站才一致。
 */
export function applyBrand(brand: Brand): void {
  if (typeof document === 'undefined') return;
  const el = document.documentElement;
  if (brand === 'blue') el.removeAttribute('data-brand');
  else el.setAttribute('data-brand', brand);
}
