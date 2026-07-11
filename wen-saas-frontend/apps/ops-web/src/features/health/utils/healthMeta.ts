import type { HealthLevel } from '@wen-saas/api-client';
import type { StatusBadge } from '@wen-saas/shared-ui';

/** 分档 → 中文标签。 */
export const HEALTH_LEVEL_LABEL: Record<HealthLevel, string> = {
  healthy: '健康',
  warning: '关注',
  danger: '危险',
};

/** 分档 → 状态徽章（复用租户 StatusPill 的 kind 色板）。后端契约为小写，做大小写归一 + 兜底，未知值不白屏。 */
export function healthLevelBadge(level: HealthLevel): StatusBadge {
  switch (String(level).toLowerCase()) {
    case 'healthy':
      return { kind: 'success', text: HEALTH_LEVEL_LABEL.healthy, color: 'green' };
    case 'warning':
      return { kind: 'warning', text: HEALTH_LEVEL_LABEL.warning, color: 'orange' };
    case 'danger':
      return { kind: 'danger', text: HEALTH_LEVEL_LABEL.danger, color: 'red' };
    default:
      return { kind: 'neutral', text: String(level ?? '—'), color: 'gray' };
  }
}

/** 分档 → 主色 CSS 变量（评分数字 / 圆点）。 */
export function healthLevelColor(level: HealthLevel): string {
  switch (String(level).toLowerCase()) {
    case 'healthy':
      return 'var(--success)';
    case 'warning':
      return 'var(--warning)';
    case 'danger':
      return 'var(--danger)';
    default:
      return 'var(--text-secondary)';
  }
}

/** 列表 / 详情筛选 Tab（含「全部」）。 */
export const HEALTH_FILTER_TABS: { key: HealthLevel | 'all'; label: string }[] = [
  { key: 'all', label: '全部' },
  { key: 'healthy', label: HEALTH_LEVEL_LABEL.healthy },
  { key: 'warning', label: HEALTH_LEVEL_LABEL.warning },
  { key: 'danger', label: HEALTH_LEVEL_LABEL.danger },
];

/** 环比 delta 渲染：+N ↑ / -N ↓ / 0 持平。 */
export function formatDelta(delta: number): { text: string; up: boolean | null } {
  if (delta > 0) return { text: `↑ ${delta}`, up: true };
  if (delta < 0) return { text: `↓ ${Math.abs(delta)}`, up: false };
  return { text: '持平', up: null };
}
