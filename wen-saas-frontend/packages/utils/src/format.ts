/**
 * 字节数 → 可读体积。空值 / NaN 显示「—」；1024 进制，KB 起一位小数，
 * 数值 ≥100 或恰为整数时去掉小数（如 100 KB / 2 MB），上限 TB。
 */
export function formatBytes(bytes: number | null | undefined): string {
  if (bytes == null || Number.isNaN(bytes)) return '—';
  if (bytes < 1024) return `${bytes} B`;
  const units = ['KB', 'MB', 'GB', 'TB'];
  let v = bytes / 1024;
  let i = 0;
  while (v >= 1024 && i < units.length - 1) {
    v /= 1024;
    i++;
  }
  return `${v.toFixed(v >= 100 || Number.isInteger(v) ? 0 : 1)} ${units[i]}`;
}

/**
 * 取文件名扩展名（大写，用于列表里的类型徽标展示）；无扩展名 / 隐藏文件
 * （首字符为「.」）统一回退 'FILE'。
 */
export function fileExt(name: string): string {
  const i = name.lastIndexOf('.');
  return i > 0 ? name.slice(i + 1).toUpperCase() : 'FILE';
}
