/**
 * 行/卡片点击导航的「选区感知」守卫。
 *
 * 列表里点行打开详情，与「划词选中文字复制」天然冲突：用户想拖选某列文字时，
 * mouseup 会触发行点击直接跳走，选区被清掉、复制不了。
 *
 * 在每个「点行/点卡片 → 选中/打开详情」的 onClick 开头调用本函数：
 * 当前存在非空文本选区时返回 true，调用方据此 `return` 跳过导航，
 * 让浏览器原生的划词 + Ctrl/⌘+C 正常工作。普通点击（无选区）照常导航。
 *
 * 纯 DOM、无副作用；放 shared-ui 供运营/租户两端及 DataTable 复用。
 */
export function shouldSkipRowNav(): boolean {
  if (typeof window === 'undefined') return false;
  const selection = window.getSelection();
  return !!selection && !selection.isCollapsed && selection.toString().trim().length > 0;
}
