import type { StatusBadge } from './StatusPill';

/**
 * StatusBadge.kind 去掉 brand 后与 @wen-saas/api-client 的语义 Tone 同一集合，
 * meta 表里带 tone 的状态可直接透传给 StatusPill，不必各端再写色映射。
 */
export type SemanticTone = Exclude<StatusBadge['kind'], 'brand'>;

/** 把「语义 tone + 文案」组装成 StatusPill 可渲染的 StatusBadge。 */
export function toneBadge(tone: SemanticTone, text: string): StatusBadge {
  return { kind: tone, text, color: '' };
}

/**
 * HTTP 状态码上色单源：<400 成功 / 4xx 警告 / 5xx 危险，空值渲染中性「—」。
 * 审计日志、API Key 调用日志、运营租户审计等所有状态码列统一走这里。
 */
export function httpStatusBadge(code: number | null | undefined): StatusBadge {
  if (code == null) return { kind: 'neutral', text: '—', color: '' };
  const kind: StatusBadge['kind'] = code < 400 ? 'success' : code < 500 ? 'warning' : 'danger';
  return { kind, text: String(code), color: '' };
}
