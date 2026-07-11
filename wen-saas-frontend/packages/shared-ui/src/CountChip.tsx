import type { ReactNode } from 'react';

export type CountChipProps = {
  /** neutral=计数/中性标记（默认）；brand=品牌浅底标签（如「实时聚合」）。 */
  tone?: 'neutral' | 'brand';
  title?: string;
  children: ReactNode;
};

/**
 * 计数/中性小芯片：承载列表计数（{items}/{total}）与「实时聚合」类中性标记，
 * 形态对齐 StatusPill（浅底 + 同色描边 + 小圆角），替代散落各页的裸 antd Tag。
 * 数字恒 tabular-nums，多行计数列宽稳定。
 */
export function CountChip({ tone = 'neutral', title, children }: CountChipProps) {
  const style =
    tone === 'brand'
      ? {
          color: 'var(--brand)',
          background: 'var(--brand-bg)',
          borderColor: 'var(--brand-bg-strong)',
        }
      : {
          color: 'var(--text-secondary)',
          background: '#F1F3F7',
          borderColor: 'var(--border)',
        };
  return (
    <span
      title={title}
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        padding: '1px 8px',
        borderRadius: 'var(--r-sm)',
        fontSize: 12,
        lineHeight: 1.6,
        fontWeight: 500,
        border: '1px solid',
        whiteSpace: 'nowrap',
        fontVariantNumeric: 'tabular-nums',
        ...style,
      }}
    >
      {children}
    </span>
  );
}
