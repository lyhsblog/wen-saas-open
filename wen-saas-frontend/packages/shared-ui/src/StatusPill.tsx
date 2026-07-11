import type { CSSProperties } from 'react';

/**
 * 状态徽章数据模型：业务侧的状态枚举先映射成 StatusBadge，再交给 StatusPill 渲染。
 * kind 决定配色语义，text 是展示文案，color 为兼容旧 antd Tag 写法保留（StatusPill 本身不读它）。
 */
export type StatusBadge = {
  kind: 'success' | 'warning' | 'danger' | 'neutral' | 'info' | 'brand';
  text: string;
  /** 兼容历史 antd Tag 用法的色名（StatusPill 渲染不依赖，但部分调用方仍读它，故保持必填）。 */
  color: string;
};

/** 状态徽章颜色 token 表：语义色浅底 + 同色系描边，随 design-system 令牌走。 */
const KIND_STYLE: Record<StatusBadge['kind'], CSSProperties> = {
  success: {
    color: 'var(--success)',
    background: 'var(--success-bg)',
    borderColor: '#BBF7D0',
  },
  warning: {
    color: 'var(--warning)',
    background: 'var(--warning-bg)',
    borderColor: '#FED7AA',
  },
  danger: {
    color: 'var(--danger)',
    background: 'var(--danger-bg)',
    borderColor: '#FECACA',
  },
  info: {
    color: 'var(--info)',
    background: 'var(--info-bg)',
    borderColor: '#BFDBFE',
  },
  neutral: {
    color: 'var(--text-secondary)',
    background: '#F1F3F7',
    borderColor: 'var(--border)',
  },
  brand: {
    color: 'var(--brand)',
    background: 'var(--brand-bg)',
    borderColor: 'var(--brand-bg-strong)',
  },
};

export type StatusPillProps = { badge: StatusBadge };

/**
 * 视觉形态：浅底色 pill + 同色描边 + 6px 同色圆点 + 深色文字。
 * 与 antd Badge / Tag 区别：是"标签型"而非"小红点型"，对齐状态语义更强。
 */
export function StatusPill({ badge }: StatusPillProps) {
  const s = KIND_STYLE[badge.kind];
  return (
    <span
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 4,
        padding: '1px 8px',
        borderRadius: 'var(--r-sm)',
        fontSize: 12,
        lineHeight: 1.6,
        fontWeight: 500,
        border: '1px solid',
        whiteSpace: 'nowrap',
        ...s,
      }}
    >
      <span
        style={{
          width: 6,
          height: 6,
          borderRadius: '50%',
          background: 'currentColor',
          display: 'inline-block',
        }}
      />
      {badge.text}
    </span>
  );
}
