import { TICKET_TYPE_LABEL, TICKET_PRIORITY_META, TICKET_STATUS_META } from '@wen-saas/api-client';
import type {
  Tone as ApiTone,
  ToneLabel,
  TicketPriority,
  TicketStatus,
  TicketType,
} from '@wen-saas/api-client';
import type { Tone } from '../../components/ui';

// 文案 / tone 单源自 @wen-saas/api-client；h5 把语义 tone 映射到本端 Tone（info→brand）。
const TO_H5_TONE: Record<ApiTone, Tone> = {
  success: 'success',
  warning: 'warning',
  danger: 'danger',
  neutral: 'neutral',
  info: 'brand',
};
function labelMap<K extends string>(m: Record<K, ToneLabel>): Record<K, string> {
  return Object.fromEntries((Object.keys(m) as K[]).map((k) => [k, m[k].label])) as Record<
    K,
    string
  >;
}
function toneMap<K extends string>(m: Record<K, ToneLabel>): Record<K, Tone> {
  return Object.fromEntries(
    (Object.keys(m) as K[]).map((k) => [k, TO_H5_TONE[m[k].tone]]),
  ) as Record<K, Tone>;
}

export const STATUS_LABEL: Record<TicketStatus, string> = labelMap(TICKET_STATUS_META);

export const STATUS_TONE: Record<TicketStatus, Tone> = toneMap(TICKET_STATUS_META);

export const TYPE_LABEL: Record<TicketType, string> = TICKET_TYPE_LABEL;

export const PRIORITY_LABEL: Record<TicketPriority, string> = labelMap(TICKET_PRIORITY_META);

export const PRIORITY_TONE: Record<TicketPriority, Tone> = toneMap(TICKET_PRIORITY_META);

/** 状态过滤 tab：value=undefined 表示全部。 */
export const STATUS_TABS: { key: string; label: string; value?: TicketStatus }[] = [
  { key: 'all', label: '全部' },
  { key: 'PENDING', label: '待处理', value: 'PENDING' },
  { key: 'PROCESSING', label: '处理中', value: 'PROCESSING' },
  { key: 'RESOLVED', label: '已解决', value: 'RESOLVED' },
];
