import { useState } from 'react';
import { Button, Empty, Table } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/lib/table';
import { CountChip, formatDate, StatusPill, toneBadge } from '@wen-saas/shared-ui';
import { AssignPlanModal } from '../modals/AssignPlanModal';
import { RenewPlanModal } from '../modals/RenewPlanModal';
import { RevokePlanModal } from '../modals/RevokePlanModal';
import type { OpsPlanView } from '@wen-saas/api-client';

type Props = {
  tenantId: string;
  plans: OpsPlanView[];
};

export function TabPlans({ tenantId, plans }: Props) {
  const [assignOpen, setAssignOpen] = useState(false);
  const [renewTarget, setRenewTarget] = useState<OpsPlanView | null>(null);
  const [revokeTarget, setRevokeTarget] = useState<OpsPlanView | null>(null);

  const columns: ColumnsType<OpsPlanView> = [
    {
      title: '套餐',
      key: 'name',
      render: (_, row) => (
        <div>
          <div style={{ fontWeight: 500 }}>{row.name || row.code}</div>
          <code style={{ fontSize: 11, color: 'var(--text-tertiary)' }}>{row.code}</code>
        </div>
      ),
    },
    {
      title: '到期时间',
      dataIndex: 'expiresAt',
      key: 'expiresAt',
      width: 200,
      render: (v: string | null) => {
        if (v === null) return <CountChip tone="brand">永久</CountChip>;
        const date = new Date(v);
        const days = Math.ceil((date.getTime() - Date.now()) / (1000 * 60 * 60 * 24));
        return (
          <span>
            {formatDate(v)}{' '}
            <span
              style={{
                fontSize: 12,
                color:
                  days < 0
                    ? 'var(--danger)'
                    : days <= 30
                      ? 'var(--warning)'
                      : 'var(--text-tertiary)',
              }}
            >
              （{days >= 0 ? `剩 ${days} 天` : `过期 ${Math.abs(days)} 天`}）
            </span>
          </span>
        );
      },
    },
    {
      title: '状态',
      dataIndex: 'readonly',
      key: 'readonly',
      width: 90,
      render: (readonly: boolean) => (
        <StatusPill
          badge={readonly ? toneBadge('danger', '只读') : toneBadge('success', '生效中')}
        />
      ),
    },
    {
      title: '操作',
      key: 'actions',
      width: 120,
      render: (_, row) => (
        <div style={{ display: 'flex', gap: 4 }}>
          <Button size="small" onClick={() => setRenewTarget(row)}>
            续费
          </Button>
          <Button size="small" danger onClick={() => setRevokeTarget(row)}>
            取消
          </Button>
        </div>
      ),
    },
  ];

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 12 }}>
        <Button
          type="primary"
          icon={<PlusOutlined />}
          size="small"
          onClick={() => setAssignOpen(true)}
        >
          勾选套餐
        </Button>
      </div>
      <Table<OpsPlanView>
        columns={columns}
        dataSource={plans}
        rowKey="code"
        pagination={false}
        size="small"
        locale={{ emptyText: <Empty description="该租户暂无套餐包" /> }}
      />

      <AssignPlanModal
        open={assignOpen}
        tenantId={tenantId}
        existingPlans={plans}
        onClose={() => setAssignOpen(false)}
      />
      {renewTarget && (
        <RenewPlanModal
          open={!!renewTarget}
          tenantId={tenantId}
          packageCode={renewTarget.code}
          packageName={renewTarget.name}
          onClose={() => setRenewTarget(null)}
        />
      )}
      {revokeTarget && (
        <RevokePlanModal
          open={!!revokeTarget}
          tenantId={tenantId}
          packageCode={revokeTarget.code}
          packageName={revokeTarget.name}
          onClose={() => setRevokeTarget(null)}
        />
      )}
    </div>
  );
}
