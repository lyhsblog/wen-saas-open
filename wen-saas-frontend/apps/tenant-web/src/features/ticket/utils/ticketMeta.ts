import { formatDateTime as sharedFormatDateTime, toneBadge } from '@wen-saas/shared-ui';
import type { StatusBadge } from '@wen-saas/shared-ui';
import {
  TICKET_TYPE_LABEL,
  TICKET_PRIORITY_META,
  TICKET_STATUS_META,
  TICKET_SLA_META,
} from '@wen-saas/api-client';
import type {
  TicketPriority,
  TicketSlaState,
  TicketStatus,
  TicketType,
} from '@wen-saas/api-client';

// 文案 / 语义 tone 单源自 @wen-saas/api-client，渲染统一走 StatusPill（toneBadge 直接透传 tone）。

/** 工单类型中文。 */
export const TYPE_LABEL: Record<TicketType, string> = TICKET_TYPE_LABEL;

/** 优先级中文 + 语义 tone + SLA 时限。 */
export const PRIORITY_META = TICKET_PRIORITY_META;

/** 状态中文 + 语义 tone。 */
export const STATUS_META = TICKET_STATUS_META;

/** SLA 实时态中文 + 语义 tone。 */
export const SLA_META = TICKET_SLA_META;

/** 优先级 → StatusPill badge。 */
export function ticketPriorityBadge(p: TicketPriority): StatusBadge {
  return toneBadge(PRIORITY_META[p].tone, PRIORITY_META[p].label);
}

/** 状态 → StatusPill badge。 */
export function ticketStatusBadge(s: TicketStatus): StatusBadge {
  return toneBadge(STATUS_META[s].tone, STATUS_META[s].label);
}

/** SLA 实时态 → StatusPill badge。 */
export function ticketSlaBadge(s: TicketSlaState): StatusBadge {
  return toneBadge(SLA_META[s].tone, SLA_META[s].label);
}

/** 列表状态筛选页签。 */
export const STATUS_TABS: { key: TicketStatus | 'all'; label: string }[] = [
  { key: 'all', label: '全部' },
  { key: 'PENDING', label: '待处理' },
  { key: 'PROCESSING', label: '处理中' },
  { key: 'RESOLVED', label: '已解决' },
  { key: 'CLOSED', label: '已关闭' },
];

export function formatDateTime(iso: string | null): string {
  return sharedFormatDateTime(iso);
}

export { fromNow } from '@wen-saas/shared-ui';

export function formatSize(bytes: string | number): string {
  const n = Number(bytes);
  if (!Number.isFinite(n)) return '';
  if (n < 1024) return `${n} B`;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`;
  return `${(n / (1024 * 1024)).toFixed(1)} MB`;
}
