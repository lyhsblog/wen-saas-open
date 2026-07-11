import type { CSSProperties, ReactNode } from 'react';

export function SectionCard({
  id,
  title,
  extra,
  hint,
  children,
  style,
}: {
  id: string;
  title: ReactNode;
  extra?: ReactNode;
  hint?: ReactNode;
  children: ReactNode;
  style?: CSSProperties;
}) {
  return (
    <section
      id={`opf-${id}`}
      style={{
        background: 'var(--bg-elev)',
        border: '1px solid var(--border)',
        borderRadius: 'var(--r-md)',
        padding: '20px 24px',
        scrollMarginTop: 80,
        ...style,
      }}
    >
      <header
        style={{
          display: 'flex',
          alignItems: 'baseline',
          justifyContent: 'space-between',
          marginBottom: hint ? 4 : 16,
          gap: 12,
        }}
      >
        <div style={{ fontSize: 16, fontWeight: 600 }}>{title}</div>
        {extra ? <div>{extra}</div> : null}
      </header>
      {hint ? (
        <div style={{ fontSize: 12, color: 'var(--text-tertiary)', marginBottom: 16 }}>{hint}</div>
      ) : null}
      {children}
    </section>
  );
}

export function FieldRow({
  label,
  value,
  hint,
  action,
  mono,
  locked,
}: {
  label: ReactNode;
  value: ReactNode;
  hint?: ReactNode;
  action?: ReactNode;
  mono?: boolean;
  locked?: boolean;
}) {
  return (
    <div
      style={{
        display: 'flex',
        gap: 16,
        padding: '10px 0',
        borderBottom: '1px solid var(--border)',
        alignItems: 'center',
      }}
    >
      <div style={{ width: 110, color: 'var(--text-tertiary)', fontSize: 13, flexShrink: 0 }}>
        {label}
      </div>
      <div
        style={{
          flex: 1,
          fontSize: 14,
          color: locked ? 'var(--text-tertiary)' : 'var(--text)',
          fontFamily: mono ? 'var(--font-mono, ui-monospace, monospace)' : undefined,
        }}
      >
        {value ?? '—'}
        {hint ? (
          <div style={{ fontSize: 11, color: 'var(--text-tertiary)', marginTop: 2 }}>{hint}</div>
        ) : null}
      </div>
      {action ? <div style={{ flexShrink: 0 }}>{action}</div> : null}
    </div>
  );
}
