import { formatDateTime as sharedFormatDateTime } from '@wen-saas/shared-ui';
import {
  TICKET_TYPE_LABEL,
  TICKET_PRIORITY_META,
  TICKET_STATUS_META,
  TICKET_SLA_META,
} from '@wen-saas/api-client';
import type {
  ToneLabel,
  TicketPriority,
  TicketSlaState,
  TicketStatus,
  TicketType,
} from '@wen-saas/api-client';
import type { StatusBadge } from '@wen-saas/shared-ui';

// 文案 / tone 单源自 @wen-saas/api-client；ops 的 `kind` 与 canonical `Tone` 同集合，直接透传。
function toKindMap<K extends string>(
  m: Record<K, ToneLabel>,
): Record<K, { label: string; kind: StatusBadge['kind'] }> {
  return Object.fromEntries(
    (Object.keys(m) as K[]).map((k) => [k, { label: m[k].label, kind: m[k].tone }]),
  ) as Record<K, { label: string; kind: StatusBadge['kind'] }>;
}

/** 工单类型中文。 */
export const TYPE_LABEL: Record<TicketType, string> = TICKET_TYPE_LABEL;

/** 优先级中文 + 徽章色 + SLA 时限文案。 */
export const PRIORITY_META: Record<
  TicketPriority,
  { label: string; kind: StatusBadge['kind']; sla: string }
> = Object.fromEntries(
  (Object.keys(TICKET_PRIORITY_META) as TicketPriority[]).map((k) => [
    k,
    {
      label: TICKET_PRIORITY_META[k].label,
      kind: TICKET_PRIORITY_META[k].tone,
      sla: TICKET_PRIORITY_META[k].sla,
    },
  ]),
) as Record<TicketPriority, { label: string; kind: StatusBadge['kind']; sla: string }>;

/** 状态中文 + 徽章色。 */
export const STATUS_META: Record<TicketStatus, { label: string; kind: StatusBadge['kind'] }> =
  toKindMap(TICKET_STATUS_META);

/** SLA 实时态中文 + 徽章色。 */
export const SLA_META: Record<TicketSlaState, { label: string; kind: StatusBadge['kind'] }> =
  toKindMap(TICKET_SLA_META);

/** kind → antd 色名（StatusPill 只读 kind，color 仅满足 StatusBadge 类型）。 */
const KIND_COLOR: Record<StatusBadge['kind'], string> = {
  success: 'green',
  warning: 'orange',
  danger: 'red',
  info: 'blue',
  neutral: 'default',
  brand: 'blue',
};

export function priorityBadge(p: TicketPriority): StatusBadge {
  const m = PRIORITY_META[p];
  return { kind: m.kind, text: m.label, color: KIND_COLOR[m.kind] };
}

export function statusBadge(s: TicketStatus): StatusBadge {
  const m = STATUS_META[s];
  return { kind: m.kind, text: m.label, color: KIND_COLOR[m.kind] };
}

export function slaBadge(s: TicketSlaState): StatusBadge {
  const m = SLA_META[s];
  return { kind: m.kind, text: m.label, color: KIND_COLOR[m.kind] };
}

/** 列表状态筛选页签。 */
export const STATUS_TABS: { key: TicketStatus | 'all'; label: string }[] = [
  { key: 'all', label: '全部' },
  { key: 'PENDING', label: '待处理' },
  { key: 'PROCESSING', label: '处理中' },
  { key: 'RESOLVED', label: '已解决' },
  { key: 'CLOSED', label: '已关闭' },
];

export { fromNow } from '@wen-saas/shared-ui';

/** 绝对时间（详情用）：上海时区 `YYYY-MM-DD HH:mm:ss`。 */
export function formatDateTime(iso: string | null): string {
  return sharedFormatDateTime(iso);
}

/** 字节数人类可读。 */
export function formatSize(bytes: string | number): string {
  const n = Number(bytes);
  if (!Number.isFinite(n)) return '';
  if (n < 1024) return `${n} B`;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`;
  return `${(n / (1024 * 1024)).toFixed(1)} MB`;
}
