import { useMemo, useState } from 'react';
import { Badge } from 'antd';
import { CountChip } from '@wen-saas/shared-ui';
import {
  DownOutlined,
  RightOutlined,
  PlusOutlined,
  AppstoreOutlined,
  LockOutlined,
} from '@ant-design/icons';
import type { DictGroup } from '@wen-saas/domain';
import { PermissionGate, ReadonlyButton } from '@wen-saas/permissions';

type Source = NonNullable<DictGroup['source']>;

const SOURCE_META: Record<Source, { label: string; desc: string; order: number }> = {
  CUSTOM: { label: '自定义', desc: '本租户自建', order: 0 },
  MODULE: { label: '业务模块', desc: '由业务模块声明', order: 1 },
  SYSTEM: { label: '系统预置', desc: '底座预置，仅可追加 / 停用', order: 2 },
};

type Props = {
  groups: DictGroup[];
  selectedCode: string | null;
  onSelect: (code: string) => void;
  onCreateGroup: () => void;
  /** 窄屏放进 NavRailDrawer 时铺满容器：宽高 100%、去掉 inline 形态的 280 定宽与右分隔线。 */
  fluid?: boolean;
};

export function DictGroupList({
  groups,
  selectedCode,
  onSelect,
  onCreateGroup,
  fluid = false,
}: Props) {
  const [openSources, setOpenSources] = useState<Set<Source>>(
    new Set<Source>(['CUSTOM', 'MODULE', 'SYSTEM']),
  );
  const groupsBySource = useMemo(() => {
    const map: Record<Source, DictGroup[]> = { CUSTOM: [], MODULE: [], SYSTEM: [] };
    for (const g of groups) {
      const src: Source = g.source ?? 'CUSTOM';
      map[src].push(g);
    }
    return map;
  }, [groups]);

  const toggle = (src: Source) => {
    setOpenSources((prev) => {
      const next = new Set(prev);
      if (next.has(src)) next.delete(src);
      else next.add(src);
      return next;
    });
  };

  return (
    <div
      style={{
        flexShrink: 0,
        background: 'var(--bg-elev)',
        display: 'flex',
        flexDirection: 'column',
        ...(fluid
          ? { width: '100%', height: '100%' }
          : { width: 280, borderRight: '1px solid var(--border)' }),
      }}
    >
      <div
        style={{
          padding: '14px 16px',
          borderBottom: '1px solid var(--divider)',
          display: 'flex',
          alignItems: 'center',
          gap: 8,
        }}
      >
        <span style={{ fontSize: 14, fontWeight: 600 }}>字典分组</span>
        <Badge count={groups.length} showZero color="var(--text-tertiary)" />
        <div style={{ flex: 1 }} />
        <PermissionGate permission="dict.group.create">
          <ReadonlyButton
            type="primary"
            size="small"
            icon={<PlusOutlined />}
            onClick={onCreateGroup}
          >
            新建
          </ReadonlyButton>
        </PermissionGate>
      </div>
      <div style={{ flex: 1, overflowY: 'auto' }}>
        {(['CUSTOM', 'MODULE', 'SYSTEM'] as Source[]).map((src) => {
          const list = groupsBySource[src];
          if (list.length === 0) return null;
          const meta = SOURCE_META[src];
          const open = openSources.has(src);
          return (
            <div key={src}>
              <div
                onClick={() => toggle(src)}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 8,
                  padding: '10px 16px',
                  background: 'var(--bg-muted)',
                  borderTop: '1px solid var(--border)',
                  borderBottom: '1px solid var(--border)',
                  cursor: 'pointer',
                  userSelect: 'none',
                }}
              >
                {open ? (
                  <DownOutlined style={{ fontSize: 10, color: 'var(--text-tertiary)' }} />
                ) : (
                  <RightOutlined style={{ fontSize: 10, color: 'var(--text-tertiary)' }} />
                )}
                {src === 'SYSTEM' ? (
                  <LockOutlined style={{ fontSize: 13, color: 'var(--text-secondary)' }} />
                ) : (
                  <AppstoreOutlined style={{ fontSize: 13, color: 'var(--text-secondary)' }} />
                )}
                <span style={{ fontSize: 13, fontWeight: 700 }}>{meta.label}</span>
                <Badge count={list.length} showZero color="var(--text-tertiary)" />
                <div style={{ flex: 1 }} />
                <span style={{ fontSize: 11, color: 'var(--text-tertiary)' }}>{meta.desc}</span>
              </div>
              {open &&
                list.map((g) => {
                  const isSel = g.code === selectedCode;
                  return (
                    <div
                      key={g.code}
                      onClick={() => g.code && onSelect(g.code)}
                      style={{
                        padding: '8px 16px',
                        cursor: 'pointer',
                        background: isSel ? 'var(--brand-bg)' : 'transparent',
                        borderLeft: `3px solid ${isSel ? 'var(--brand)' : 'transparent'}`,
                      }}
                    >
                      <div
                        style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 2 }}
                      >
                        <span style={{ fontSize: 13, fontWeight: 500 }}>{g.name}</span>
                        {g.supportHierarchy && <CountChip tone="brand">树形</CountChip>}
                      </div>
                      <div
                        style={{
                          fontSize: 11,
                          color: 'var(--text-tertiary)',
                          fontFamily: 'var(--font-mono, monospace)',
                        }}
                      >
                        {g.code}
                      </div>
                    </div>
                  );
                })}
            </div>
          );
        })}
      </div>
    </div>
  );
}

export { SOURCE_META };
export type { Source };
