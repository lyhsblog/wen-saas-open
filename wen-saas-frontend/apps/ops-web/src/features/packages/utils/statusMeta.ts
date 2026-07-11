import type { PackageStatus } from '@wen-saas/api-client';
import type { StatusBadge } from '@wen-saas/shared-ui';

/** 套餐 4 态 → 状态徽章（复用租户管理的 StatusPill 配色语义）。状态值为 boot 响应的大写（OPS-P-032）。 */
export const PACKAGE_STATUS_BADGE: Record<PackageStatus, StatusBadge> = {
  DRAFT: { kind: 'neutral', text: '草稿', color: 'default' },
  ACTIVE: { kind: 'success', text: '已上架', color: 'green' },
  INACTIVE: { kind: 'warning', text: '已下架', color: 'orange' },
  ARCHIVED: { kind: 'neutral', text: '已归档', color: 'default' },
};

const FALLBACK_BADGE: StatusBadge = { kind: 'neutral', text: '未知', color: 'default' };

/** 取状态徽章，未知状态兜底为「未知」neutral，避免渲染崩溃。 */
export function packageStatusBadge(status: string): StatusBadge {
  return PACKAGE_STATUS_BADGE[status as PackageStatus] ?? FALLBACK_BADGE;
}

/** 列表排序权重：上架 > 草稿 > 下架 > 归档。 */
export const PACKAGE_STATUS_ORDER: Record<PackageStatus, number> = {
  ACTIVE: 0,
  DRAFT: 1,
  INACTIVE: 2,
  ARCHIVED: 3,
};

/** 状态筛选下拉项。 */
export const PACKAGE_STATUS_FILTER_OPTIONS: { label: string; value: PackageStatus }[] = [
  { label: '已上架', value: 'ACTIVE' },
  { label: '草稿', value: 'DRAFT' },
  { label: '已下架', value: 'INACTIVE' },
  { label: '已归档', value: 'ARCHIVED' },
];

/**
 * 「永久有效」在表单 Select 中的哨兵值。
 * boot 用 `defaultTtlDays: null` 表示永久，但 AntD Select 的 option value 不能为 null，
 * 故用 0 作哨兵（真实 TTL 最小 30，0 不会与有效天数冲突），在表单边界 0 ↔ null 互转。
 */
export const TTL_PERMANENT = 0;

/** 默认有效期天数下拉项；TTL_PERMANENT(0) = 永久。 */
export const TTL_OPTIONS: { label: string; value: number }[] = [
  { label: '永久有效', value: TTL_PERMANENT },
  { label: '30 天', value: 30 },
  { label: '90 天', value: 90 },
  { label: '180 天', value: 180 },
  { label: '365 天', value: 365 },
  { label: '730 天', value: 730 },
];

/** boot 的 defaultTtlDays(null=永久) → 表单 Select 值（0=永久）。 */
export function ttlToFormValue(defaultTtlDays: number | null): number {
  return defaultTtlDays == null ? TTL_PERMANENT : defaultTtlDays;
}

/** 表单 Select 值（0=永久）→ boot 的 defaultTtlDays（null=永久）。 */
export function ttlFromFormValue(value: number): number | null {
  return value === TTL_PERMANENT ? null : value;
}

export function ttlLabel(defaultTtlDays: number | null): string {
  return defaultTtlDays == null ? '永久' : `${defaultTtlDays} 天`;
}
