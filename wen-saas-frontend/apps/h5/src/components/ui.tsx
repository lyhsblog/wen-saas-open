import { type ReactNode } from 'react';

/** 轻量 tag/badge（贴合原型药丸样式），按 tone 取色。 */
export type Tone = 'brand' | 'danger' | 'warning' | 'success' | 'neutral';

const TONE: Record<Tone, { bg: string; color: string }> = {
  brand: { bg: 'var(--brand-bg)', color: 'var(--brand)' },
  danger: { bg: 'var(--danger-bg, #fef2f2)', color: 'var(--danger, #dc2626)' },
  warning: { bg: 'var(--warning-bg, #fff7ed)', color: 'var(--warning, #d97706)' },
  success: { bg: 'var(--success-bg, #ecfdf3)', color: 'var(--success, #16a34a)' },
  neutral: { bg: 'var(--bg-muted, #f1f5f9)', color: 'var(--text-secondary)' },
};

export function Tag({ tone = 'neutral', children }: { tone?: Tone; children: ReactNode }) {
  const t = TONE[tone];
  return (
    <span
      style={{
        fontSize: 11,
        fontWeight: 600,
        padding: '1px 7px',
        borderRadius: 999,
        background: t.bg,
        color: t.color,
        lineHeight: 1.6,
        whiteSpace: 'nowrap',
      }}
    >
      {children}
    </span>
  );
}

/** 白卡容器。 */
export function Card({ children, style }: { children: ReactNode; style?: React.CSSProperties }) {
  return <div style={{ background: 'var(--bg-elev)', borderRadius: 12, ...style }}>{children}</div>;
}

/** 分组小标题。 */
export function SectionTitle({ children }: { children: ReactNode }) {
  return (
    <div
      style={{
        fontSize: 11,
        color: 'var(--text-tertiary)',
        fontWeight: 500,
        padding: '0 4px 6px',
        letterSpacing: 0.3,
      }}
    >
      {children}
    </div>
  );
}
