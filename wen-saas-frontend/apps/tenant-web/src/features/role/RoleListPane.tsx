import { useMemo, useState } from 'react';
import { shouldSkipRowNav } from '@wen-saas/shared-ui';
import { Badge, Input } from 'antd';
import { StatusPill, toneBadge } from '@wen-saas/shared-ui';
import { LockOutlined, PlusOutlined, TeamOutlined } from '@ant-design/icons';
import type { RoleView } from '@wen-saas/domain';
import { PermissionGate, ReadonlyButton } from '@wen-saas/permissions';

type Props = {
  roles: RoleView[];
  selectedId: string | null;
  onSelect: (id: string) => void;
  onCreateRole: () => void;
  onOpenRecommended: () => void;
};

export function RoleListPane({
  roles,
  selectedId,
  onSelect,
  onCreateRole,
  onOpenRecommended,
}: Props) {
  const [search, setSearch] = useState('');

  const filtered = useMemo(() => {
    if (!search.trim()) return roles;
    const q = search.toLowerCase();
    return roles.filter(
      (r) =>
        (r.name ?? '').toLowerCase().includes(q) ||
        (r.code ?? '').toLowerCase().includes(q) ||
        (r.description ?? '').toLowerCase().includes(q),
    );
  }, [roles, search]);

  const presetRoles = filtered.filter((r) => r.systemPreset);
  const customRoles = filtered.filter((r) => !r.systemPreset);

  const [openGroups, setOpenGroups] = useState<Set<string>>(new Set(['preset', 'custom']));
  const toggleGroup = (key: string) => {
    setOpenGroups((prev) => {
      const next = new Set(prev);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      return next;
    });
  };

  const renderGroup = (key: string, label: string, list: RoleView[]) => {
    if (list.length === 0) return null;
    const open = openGroups.has(key);
    return (
      <div key={key}>
        <div
          onClick={() => toggleGroup(key)}
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 8,
            padding: '9px 16px',
            background: 'var(--bg-muted)',
            borderTop: '1px solid var(--border)',
            borderBottom: '1px solid var(--border)',
            cursor: 'pointer',
            userSelect: 'none',
          }}
        >
          {key === 'preset' ? (
            <LockOutlined style={{ fontSize: 12, color: 'var(--text-tertiary)' }} />
          ) : (
            <TeamOutlined style={{ fontSize: 12, color: 'var(--text-tertiary)' }} />
          )}
          <span style={{ fontSize: 13, fontWeight: 700 }}>{label}</span>
          <Badge count={list.length} showZero color="var(--text-tertiary)" />
        </div>
        {open &&
          list.map((role) => {
            const isSel = String(role.id) === selectedId;
            return (
              <div
                key={role.id}
                onClick={() => {
                  if (shouldSkipRowNav()) return;
                  onSelect(String(role.id));
                }}
                style={{
                  padding: '9px 16px',
                  cursor: 'pointer',
                  background: isSel ? 'var(--brand-bg)' : 'transparent',
                  borderLeft: `3px solid ${isSel ? 'var(--brand)' : 'transparent'}`,
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 2 }}>
                  {role.systemPreset && (
                    <LockOutlined style={{ fontSize: 11, color: 'var(--text-tertiary)' }} />
                  )}
                  <span
                    style={{
                      fontSize: 13,
                      fontWeight: 500,
                      flex: 1,
                      overflow: 'hidden',
                      textOverflow: 'ellipsis',
                      whiteSpace: 'nowrap',
                    }}
                  >
                    {role.name}
                  </span>
                  {role.status === 0 && <StatusPill badge={toneBadge('danger', '停用')} />}
                </div>
                <div
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 6,
                    fontSize: 11,
                    color: 'var(--text-tertiary)',
                  }}
                >
                  <span style={{ fontFamily: 'var(--font-mono, monospace)' }}>{role.code}</span>
                  <span style={{ marginLeft: 'auto' }}>{role.memberCount} 名成员</span>
                </div>
              </div>
            );
          })}
      </div>
    );
  };

  return (
    <div
      style={{
        width: 304,
        flexShrink: 0,
        background: 'var(--bg-elev)',
        borderRight: '1px solid var(--border)',
        display: 'flex',
        flexDirection: 'column',
      }}
    >
      <div
        style={{
          padding: '14px 16px 10px',
          borderBottom: '1px solid var(--divider)',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 10 }}>
          <span style={{ fontSize: 14, fontWeight: 600 }}>角色列表</span>
          <Badge count={roles.length} showZero color="var(--text-tertiary)" />
          <div style={{ flex: 1 }} />
          <PermissionGate permission="role.create">
            <ReadonlyButton size="small" icon={<TeamOutlined />} onClick={onOpenRecommended}>
              推荐角色
            </ReadonlyButton>
          </PermissionGate>
          <PermissionGate permission="role.create">
            <ReadonlyButton
              type="primary"
              size="small"
              icon={<PlusOutlined />}
              onClick={onCreateRole}
            >
              新建角色
            </ReadonlyButton>
          </PermissionGate>
        </div>
        <Input.Search
          size="small"
          placeholder="搜索名称/编码"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          allowClear
        />
      </div>
      <div style={{ flex: 1, overflowY: 'auto' }}>
        {renderGroup('preset', '系统预置角色', presetRoles)}
        {renderGroup('custom', '自定义角色', customRoles)}
        {filtered.length === 0 && (
          <div style={{ padding: '32px 16px', textAlign: 'center', color: 'var(--text-tertiary)' }}>
            无匹配角色
          </div>
        )}
      </div>
    </div>
  );
}
