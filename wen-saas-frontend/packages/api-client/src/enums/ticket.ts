/**
 * 工单枚举展示元数据——**单一真相源**（ops / tenant / h5 三端共用）。
 *
 * 各端保留自己的 `ticketMeta.ts` 导出面，但文案 / tone 一律派生自此处；配色由各端按 {@link Tone} 自映射。
 */
import type { Tone, ToneLabel } from './common';
import type { TicketType, TicketPriority, TicketStatus, TicketSlaState } from '../ticket';

/** 工单类型中文（无 tone）。 */
export const TICKET_TYPE_LABEL: Record<TicketType, string> = {
  BUG: '报障',
  CONSULT: '咨询',
  SUGGESTION: '建议',
  COMPLAINT: '投诉',
};

/** 优先级：文案 + tone + SLA 时限。 */
export interface TicketPriorityMeta extends ToneLabel {
  sla: string;
}
export const TICKET_PRIORITY_META: Record<TicketPriority, TicketPriorityMeta> = {
  LOW: { label: '低', tone: 'neutral', sla: '7 天' },
  NORMAL: { label: '普通', tone: 'info', sla: '72 小时' },
  HIGH: { label: '重要', tone: 'warning', sla: '48 小时' },
  URGENT: { label: '紧急', tone: 'danger', sla: '24 小时' },
};

/** 工单状态：文案 + tone。 */
export const TICKET_STATUS_META: Record<TicketStatus, ToneLabel> = {
  PENDING: { label: '待处理', tone: 'warning' },
  PROCESSING: { label: '处理中', tone: 'info' },
  RESOLVED: { label: '已解决', tone: 'success' },
  CLOSED: { label: '已关闭', tone: 'neutral' },
};

/** SLA 实时态：文案 + tone。 */
export const TICKET_SLA_META: Record<TicketSlaState, ToneLabel> = {
  normal: { label: 'SLA 正常', tone: 'success' },
  warning: { label: 'SLA 临近', tone: 'warning' },
  breach: { label: 'SLA 超时', tone: 'danger' },
};

export type { Tone };
