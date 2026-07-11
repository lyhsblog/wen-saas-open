import type { ActivationStatus, TenantListItemView } from '@wen-saas/api-client';

import type { StatusBadge } from '@wen-saas/shared-ui';
export type { StatusBadge };

/** 激活态 → StatusPill badge（TabBasic 与 statusBadgeOf 共用）。 */
export const ACTIVATION_MAP: Record<ActivationStatus, StatusBadge> = {
  pending_activation: { kind: 'info', text: '待激活', color: '' },
  activated: { kind: 'success', text: '已激活', color: '' },
  paused: { kind: 'danger', text: '已暂停', color: '' },
  expired: { kind: 'neutral', text: '已过期', color: '' },
  unknown: { kind: 'neutral', text: '—', color: '' },
};

/** 租户综合状态：status（暂停/过期）优先，正常态回退到激活态展示。 */
export function statusBadgeOf(
  tenant: Pick<TenantListItemView, 'status' | 'activationStatus'>,
): StatusBadge {
  if (tenant.status === 0) return { kind: 'danger', text: '已暂停', color: '' };
  if (tenant.status === 2) return { kind: 'neutral', text: '已过期', color: '' };
  return ACTIVATION_MAP[tenant.activationStatus] ?? ACTIVATION_MAP.unknown;
}

/** 主管理员账号状态 → StatusPill badge。 */
export const USER_STATUS_MAP: Record<number, StatusBadge> = {
  0: { kind: 'warning', text: '未激活', color: '' },
  1: { kind: 'success', text: '正常', color: '' },
};
