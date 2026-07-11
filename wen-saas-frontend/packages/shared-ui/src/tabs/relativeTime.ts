/**
 * 把过去的时间戳格式化成「多久之前」的简短中文（用于页签「多久没访问」）。
 * 粒度：刚刚(<10s) / N 秒前 / N 分钟前 / N 小时前 / N 天前。未来或缺省时间返回「刚刚」。
 * 秒档是为「标签页较多」弹窗保留区分度——它常在短时间连开多页签时触发，全落 1 分钟内。
 */
export function formatRelativeFromNow(ts: number | undefined, now: number = Date.now()): string {
  if (!ts) return '刚刚';
  const diff = now - ts;
  if (diff < 10_000) return '刚刚';
  if (diff < 60_000) return `${Math.floor(diff / 1_000)} 秒前`;
  const min = Math.floor(diff / 60_000);
  if (min < 60) return `${min} 分钟前`;
  const hour = Math.floor(min / 60);
  if (hour < 24) return `${hour} 小时前`;
  const day = Math.floor(hour / 24);
  return `${day} 天前`;
}
