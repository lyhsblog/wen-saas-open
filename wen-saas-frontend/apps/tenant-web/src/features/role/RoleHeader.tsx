import { Alert, Popconfirm, Tabs, Tooltip } from 'antd';
import { CountChip, StatusPill, toneBadge } from '@wen-saas/shared-ui';
import {
  DeleteOutlined,
  LockOutlined,
  PauseCircleOutlined,
  PlayCircleOutlined,
  TeamOutlined,
} from '@ant-design/icons';
import type { RoleDetail } from '@wen-saas/domain';
import { TENANT_DATA_SCOPE_LABEL, type TenantDataScope } from '@wen-saas/api-client';
import { PermissionGate, ReadonlyButton } from '@wen-saas/permissions';

export type RoleTabKey = 'basic' | 'permissions' | 'dataScope' | 'members';

type Props = {
  detail: RoleDetail;
  activeTab: RoleTabKey;
  onTabChange: (key: RoleTabKey) => void;
  onToggleStatus: (enabled: boolean) => void;
  onDelete: () => void;
};

export function RoleHeader({ detail, activeTab, onTabChange, onToggleStatus, onDelete }: Props) {
  const isOwner = detail.code === 'SYSTEM_OWNER';
  const isPreset = detail.systemPreset;
  const hasMembers = (detail.memberCount ?? 0) > 0;
  const isActive = detail.status === 1;

  return (
    <div
      style={{
        background: 'var(--bg-elev)',
        borderBottom: '1px solid var(--border)',
        paddingBottom: 0,
      }}
    >
      {isOwner && (
        <Alert
          type="warning"
          showIcon
          message="主管理员角色拥有全部权限且不可修改"
          style={{ borderRadius: 0, border: 'none', borderBottom: '1px solid var(--border)' }}
        />
      )}
      <div style={{ padding: '16px 24px 0' }}>
        <div style={{ display: 'flex', alignItems: 'flex-start', gap: 12, marginBottom: 12 }}>
          <div
            style={{
              width: 44,
              height: 44,
              borderRadius: 'var(--r-md)',
              background: isOwner
                ? 'linear-gradient(135deg, var(--brand), oklch(0.55 0.18 280))'
                : 'var(--bg-muted)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              flexShrink: 0,
            }}
          >
            {isOwner ? (
              <LockOutlined style={{ fontSize: 20, color: '#fff' }} />
            ) : (
              <TeamOutlined style={{ fontSize: 20, color: 'var(--text-secondary)' }} />
            )}
          </div>
          <div style={{ flex: 1, minWidth: 0 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
              <span style={{ fontSize: 16, fontWeight: 600 }}>{detail.name}</span>
              {isPreset && <CountChip tone="brand">{isOwner ? '特权' : '预置'}</CountChip>}
              {!isActive && <StatusPill badge={toneBadge('danger', '停用')} />}
            </div>
            {detail.description && (
              <div style={{ fontSize: 13, color: 'var(--text-secondary)', marginTop: 2 }}>
                {detail.description}
              </div>
            )}
            <div
              style={{
                display: 'flex',
                gap: 16,
                marginTop: 4,
                fontSize: 12,
                color: 'var(--text-tertiary)',
                flexWrap: 'wrap',
              }}
            >
              <span style={{ fontFamily: 'var(--font-mono, monospace)' }}>{detail.code}</span>
              <span>
                数据范围：
                {TENANT_DATA_SCOPE_LABEL[detail.dataScope as TenantDataScope] ?? detail.dataScope}
              </span>
              <span>{detail.memberCount} 名成员</span>
            </div>
          </div>
          <div style={{ display: 'flex', gap: 8, flexShrink: 0 }}>
            <PermissionGate permission="role.update">
              <Tooltip title={isOwner ? '主管理员角色不可修改' : undefined}>
                <ReadonlyButton
                  size="small"
                  icon={isActive ? <PauseCircleOutlined /> : <PlayCircleOutlined />}
                  onClick={() => onToggleStatus(!isActive)}
                  {...(isOwner ? { disabled: true } : {})}
                >
                  {isActive ? '停用' : '启用'}
                </ReadonlyButton>
              </Tooltip>
            </PermissionGate>
            <PermissionGate permission="role.delete">
              <Tooltip
                title={
                  isPreset ? '系统预置角色不可删除' : hasMembers ? '请先移除全部成员' : undefined
                }
              >
                <Popconfirm
                  title={`确认删除角色「${detail.name}」？`}
                  okText="删除"
                  okButtonProps={{ danger: true }}
                  cancelText="取消"
                  disabled={isPreset || hasMembers}
                  onConfirm={onDelete}
                >
                  <ReadonlyButton
                    size="small"
                    danger
                    icon={<DeleteOutlined />}
                    {...(isPreset || hasMembers ? { disabled: true } : {})}
                  >
                    删除
                  </ReadonlyButton>
                </Popconfirm>
              </Tooltip>
            </PermissionGate>
          </div>
        </div>
        <Tabs
          activeKey={activeTab}
          onChange={(k) => onTabChange(k as RoleTabKey)}
          items={[
            { key: 'basic', label: '基本信息' },
            { key: 'permissions', label: '功能权限' },
            { key: 'dataScope', label: '数据权限' },
            { key: 'members', label: `成员 (${detail.memberCount ?? 0})` },
          ]}
          style={{ marginBottom: 0 }}
        />
      </div>
    </div>
  );
}
