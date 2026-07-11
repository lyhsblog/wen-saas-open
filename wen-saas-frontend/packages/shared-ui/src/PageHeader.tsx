import { type ReactNode } from 'react';
import { Breadcrumb } from 'antd';
import { Link } from 'react-router';

export type Crumb = {
  label: ReactNode;
  to?: string;
};

export type PageHeaderProps = {
  title: ReactNode;
  description?: ReactNode;
  breadcrumb?: Crumb[];
  extra?: ReactNode;
};

export function PageHeader({ title, description, breadcrumb, extra }: PageHeaderProps) {
  return (
    <div
      style={{
        padding: '16px 24px 12px',
        background: 'var(--bg-elev)',
        borderBottom: '1px solid var(--divider)',
      }}
    >
      {breadcrumb && breadcrumb.length > 0 && (
        <Breadcrumb
          style={{ marginBottom: 6, fontSize: 12 }}
          items={breadcrumb.map((c, i) => ({
            key: i,
            title: c.to ? <Link to={c.to}>{c.label}</Link> : c.label,
          }))}
        />
      )}
      <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
        <h1
          style={{
            margin: 0,
            fontSize: 18,
            fontWeight: 600,
            color: 'var(--text)',
            lineHeight: '24px',
          }}
        >
          {title}
        </h1>
        <div style={{ flex: 1 }} />
        {extra}
      </div>
      {description && (
        <div style={{ fontSize: 12, color: 'var(--text-secondary)', marginTop: 6 }}>
          {description}
        </div>
      )}
    </div>
  );
}
