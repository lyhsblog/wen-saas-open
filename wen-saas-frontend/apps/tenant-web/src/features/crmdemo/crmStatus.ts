import type { StatusBadge } from '@wen-saas/shared-ui';

/** 客户阶段状态机展示 + 允许流转（与后端 CustomerStatus 对齐）。kind 决定 StatusPill 配色语义。 */
export const STATUS_META: Record<
  string,
  { label: string; kind: StatusBadge['kind']; next: string[] }
> = {
  LEAD: { label: '潜在', kind: 'brand', next: ['FOLLOWING', 'LOST'] },
  FOLLOWING: { label: '跟进中', kind: 'warning', next: ['WON', 'LOST'] },
  WON: { label: '成交', kind: 'success', next: [] },
  LOST: { label: '流失', kind: 'neutral', next: [] },
};

export const STATUS_OPTIONS = Object.entries(STATUS_META).map(([value, m]) => ({
  value,
  label: m.label,
}));

/** 状态 → StatusPill 徽章；label 缺省时回落枚举中文名 / 原始值。 */
export function statusBadge(status: string, label?: string | null): StatusBadge {
  return {
    kind: STATUS_META[status]?.kind ?? 'neutral',
    text: label ?? STATUS_META[status]?.label ?? status,
    color: '', // StatusBadge 兼容字段，StatusPill 渲染不读
  };
}
