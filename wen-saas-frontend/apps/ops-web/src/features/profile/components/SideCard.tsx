import { Button, Tooltip } from 'antd';
import { CountChip, shouldSkipRowNav } from '@wen-saas/shared-ui';
import { LogoutOutlined } from '@ant-design/icons';
import type { OpsProfileBasic, OpsProfileRoleBrief } from '@wen-saas/api-client';
import { SECTIONS, type SectionId } from '../constants';
import { AvatarUploader } from './AvatarUploader';

export function SideCard({
  basic,
  roles,
  activeId,
  onSelect,
  onLogout,
}: {
  basic: OpsProfileBasic | undefined;
  roles: OpsProfileRoleBrief[] | undefined;
  activeId: SectionId;
  onSelect: (id: SectionId) => void;
  onLogout: () => void;
}) {
  const initial = (basic?.realName ?? basic?.username ?? 'U').slice(0, 1);
  return (
    <aside
      style={{
        position: 'sticky',
        top: 24,
        width: 240,
        flexShrink: 0,
        display: 'flex',
        flexDirection: 'column',
        gap: 16,
        padding: 20,
        background: 'var(--bg-elev)',
        border: '1px solid var(--border)',
        borderRadius: 'var(--r-md)',
        alignSelf: 'flex-start',
      }}
    >
      <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 8 }}>
        <AvatarUploader avatarUrl={basic?.avatarUrl} initial={initial} size={72} />
        <div style={{ fontSize: 11, color: 'var(--text-tertiary)' }}>
          点击头像更换 · JPG/PNG · ≤ 1MB
        </div>
        <div style={{ fontSize: 16, fontWeight: 600 }}>{basic?.realName ?? '—'}</div>
        <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap', justifyContent: 'center' }}>
          {(roles ?? []).map((r) => (
            <CountChip key={r.id ?? r.code}>{r.name ?? r.code}</CountChip>
          ))}
        </div>
        <Tooltip title={basic?.email}>
          <div
            style={{
              fontSize: 12,
              color: 'var(--text-tertiary)',
              maxWidth: 180,
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
            }}
          >
            {basic?.email ?? '—'}
          </div>
        </Tooltip>
      </div>

      <nav style={{ borderTop: '1px solid var(--border)', paddingTop: 12 }}>
        {SECTIONS.map((s) => {
          const active = s.id === activeId;
          return (
            <button
              key={s.id}
              type="button"
              onClick={() => {
                if (shouldSkipRowNav()) return;
                onSelect(s.id);
              }}
              style={{
                display: 'block',
                width: '100%',
                textAlign: 'left',
                padding: '6px 10px',
                background: active ? 'var(--brand-bg)' : 'transparent',
                color: active ? 'var(--brand)' : 'var(--text-secondary)',
                border: 'none',
                borderRadius: 'var(--r-sm)',
                fontSize: 13,
                fontWeight: active ? 600 : 400,
                cursor: 'pointer',
                marginBottom: 2,
              }}
            >
              {s.label}
            </button>
          );
        })}
      </nav>

      <Button
        block
        danger
        type="text"
        icon={<LogoutOutlined />}
        onClick={onLogout}
        style={{ marginTop: 'auto' }}
      >
        退出登录
      </Button>
    </aside>
  );
}
