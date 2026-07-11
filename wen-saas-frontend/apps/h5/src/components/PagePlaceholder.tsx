import { type ReactNode } from 'react';
import { NavBar } from 'antd-mobile';

/** 阶段一占位页：固定标题栏 + 建设中提示。阶段二/三逐页替换为真实内容。 */
export function PagePlaceholder({
  title,
  hint,
  extra,
}: {
  title: string;
  hint: string;
  extra?: ReactNode;
}) {
  return (
    <div style={{ minHeight: '100%', background: 'var(--bg)' }}>
      <NavBar backArrow={false} style={{ background: 'var(--bg-elev)' }}>
        {title}
      </NavBar>
      <div
        style={{
          padding: '48px 24px',
          textAlign: 'center',
          color: 'var(--text-tertiary)',
          fontSize: 14,
        }}
      >
        {hint}
      </div>
      {extra}
    </div>
  );
}
