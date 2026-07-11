import { EditOutlined } from '@ant-design/icons';
import { formatDateTime } from '@wen-saas/shared-ui';
import type { RoleDetail } from '@wen-saas/domain';
import { TENANT_DATA_SCOPE_LABEL, type TenantDataScope } from '@wen-saas/api-client';
import { PermissionGate, ReadonlyButton } from '@wen-saas/permissions';

type Props = {
  detail: RoleDetail;
  onEdit: () => void;
};

function formatDate(iso: string | undefined) {
  return formatDateTime(iso);
}

export function RoleBasicTab({ detail, onEdit }: Props) {
  const isOwner = detail.code === 'SYSTEM_OWNER';
  const rows: { label: string; value: React.ReactNode }[] = [
    { label: '角色名称', value: detail.name },
    {
      label: '角色编码',
      value: (
        <span style={{ fontFamily: 'var(--font-mono, monospace)', fontSize: 13 }}>
          {detail.code}
        </span>
      ),
    },
    { label: '来源', value: detail.systemPreset ? '系统预置' : '自定义' },
    { label: '状态', value: detail.status === 1 ? '启用' : '停用' },
    {
      label: '默认数据范围',
      value: TENANT_DATA_SCOPE_LABEL[detail.dataScope as TenantDataScope] ?? detail.dataScope,
    },
    { label: '成员数量', value: `${detail.memberCount} 名` },
    { label: '创建时间', value: formatDate(detail.createdAt) },
    { label: '最近修改', value: formatDate(detail.updatedAt) },
  ];

  return (
    <div style={{ padding: '20px 24px' }}>
      <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 16 }}>
        <PermissionGate permission="role.update">
          <ReadonlyButton
            size="small"
            icon={<EditOutlined />}
            onClick={onEdit}
            {...(isOwner ? { disabled: true } : {})}
          >
            编辑
          </ReadonlyButton>
        </PermissionGate>
      </div>
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: '1fr 1fr',
          gap: '12px 32px',
          marginBottom: 16,
        }}
      >
        {rows.map(({ label, value }) => (
          <div key={label}>
            <div style={{ fontSize: 12, color: 'var(--text-tertiary)', marginBottom: 2 }}>
              {label}
            </div>
            <div style={{ fontSize: 14 }}>{value}</div>
          </div>
        ))}
      </div>
      {detail.description && (
        <div style={{ gridColumn: '1/-1', marginTop: 4 }}>
          <div style={{ fontSize: 12, color: 'var(--text-tertiary)', marginBottom: 2 }}>描述</div>
          <div style={{ fontSize: 14, color: 'var(--text-secondary)' }}>{detail.description}</div>
        </div>
      )}
    </div>
  );
}
