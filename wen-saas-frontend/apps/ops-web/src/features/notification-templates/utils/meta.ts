import type {
  NotificationTemplateStatus,
  NotificationTemplatePriority,
  NotificationVarDef,
} from '@wen-saas/api-client';
import type { StatusBadge } from '@wen-saas/shared-ui';

/** 状态 → 徽章（复用租户管理 StatusPill 配色语义）。 */
export const STATUS_BADGE: Record<NotificationTemplateStatus, StatusBadge> = {
  ACTIVE: { kind: 'success', text: '启用', color: 'green' },
  INACTIVE: { kind: 'neutral', text: '停用', color: 'default' },
};

const FALLBACK_BADGE: StatusBadge = { kind: 'neutral', text: '未知', color: 'default' };

export function statusBadge(status: string): StatusBadge {
  return STATUS_BADGE[status as NotificationTemplateStatus] ?? FALLBACK_BADGE;
}

export const STATUS_FILTER_OPTIONS: { label: string; value: NotificationTemplateStatus }[] = [
  { label: '启用', value: 'ACTIVE' },
  { label: '停用', value: 'INACTIVE' },
];

export const PRIORITY_OPTIONS: { label: string; value: NotificationTemplatePriority }[] = [
  { label: '普通', value: 'NORMAL' },
  { label: '重要', value: 'IMPORTANT' },
];

export function priorityLabel(p: string): string {
  return p === 'IMPORTANT' ? '重要' : '普通';
}

/** 通知类别枚举（对齐 boot NotificationCategory）。空表示不参与偏好过滤。 */
export const CATEGORY_OPTIONS: { label: string; value: string }[] = [
  { label: '待办与任务', value: 'TODO_TASK' },
  { label: '工单告警', value: 'TICKET_ALERT' },
  { label: '客户健康度', value: 'CUSTOMER_HEALTH' },
  { label: '套餐到期', value: 'PACKAGE_EXPIRY' },
  { label: '系统公告', value: 'SYSTEM_ANNOUNCEMENT' },
  { label: '安全提醒', value: 'SECURITY_ALERT' },
];

export function categoryLabel(code: string | null): string {
  if (!code) return '不限（不参与偏好过滤）';
  return CATEGORY_OPTIONS.find((o) => o.value === code)?.label ?? code;
}

/** 渠道展示顺序（与 boot ChannelMeta 一致，STATION 保底置首）。 */
export const CHANNEL_ORDER = ['STATION', 'EMAIL', 'SMS', 'WECHAT', 'WECOM', 'DINGTALK'];

/** 渠道枚举 → 中文标签（未知码兜底原值）。 */
const CHANNEL_LABEL: Record<string, string> = {
  STATION: '站内信',
  EMAIL: '邮件',
  SMS: '短信',
  WECHAT: '微信',
  WECOM: '企业微信',
  DINGTALK: '钉钉',
};

export function channelLabel(code: string): string {
  return CHANNEL_LABEL[code] ?? code;
}

/**
 * 用变量示例值渲染文案预览：把 `{name}` 替换为 sampleValue；
 * 未声明 / 无示例值的占位符保留原文（不报错、不跳过）。
 */
export function renderWithVars(
  text: string | null | undefined,
  vars: NotificationVarDef[],
): string {
  if (!text) return '';
  const sampleByName = new Map(vars.map((v) => [v.name, v.sampleValue ?? '']));
  return text.replace(/\{([a-zA-Z0-9_]+)\}/g, (whole, name: string) => {
    const sample = sampleByName.get(name);
    return sample ? sample : whole;
  });
}
