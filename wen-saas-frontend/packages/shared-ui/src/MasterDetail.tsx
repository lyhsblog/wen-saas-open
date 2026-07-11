import { type ReactNode } from 'react';

export type MasterDetailProps = {
  sidebar: ReactNode;
  /** 详情区上方可固定一行（如 PageHeader / 工具栏），不参与滚动。 */
  header?: ReactNode;
  children: ReactNode;
  /** 主侧栏宽度，默认 280。 */
  sidebarWidth?: number;
};

export function MasterDetail({ sidebar, header, children, sidebarWidth = 280 }: MasterDetailProps) {
  return (
    <div style={{ display: 'flex', height: '100%', overflow: 'hidden' }}>
      <div
        style={{
          width: sidebarWidth,
          flexShrink: 0,
          background: 'var(--bg-elev)',
          borderRight: '1px solid var(--border)',
          display: 'flex',
          flexDirection: 'column',
          overflow: 'hidden',
        }}
      >
        {sidebar}
      </div>
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', minWidth: 0 }}>
        {header}
        <div style={{ flex: 1, overflow: 'hidden' }}>{children}</div>
      </div>
    </div>
  );
}
