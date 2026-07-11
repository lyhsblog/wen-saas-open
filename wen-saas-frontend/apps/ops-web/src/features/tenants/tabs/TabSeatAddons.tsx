import { useState } from 'react';
import { Button, Card, Empty, Statistic, Table } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/lib/table';
import { formatDateTime } from '@wen-saas/shared-ui';
import { AddSeatAddonModal } from '../modals/AddSeatAddonModal';
import type { OpsSeatAddonHistoryItem, OpsSeatAddonView } from '@wen-saas/api-client';

type Props = {
  tenantId: string;
  seatAddons: OpsSeatAddonView;
};

const columns: ColumnsType<OpsSeatAddonHistoryItem> = [
  {
    title: '数量',
    dataIndex: 'quantity',
    key: 'quantity',
    width: 80,
    render: (n: number) => `+${n}`,
  },
  {
    title: '备注',
    dataIndex: 'remark',
    key: 'remark',
    render: (v: string | null) => v ?? '—',
  },
  {
    title: '操作人',
    key: 'operator',
    width: 140,
    render: (_: unknown, r: OpsSeatAddonHistoryItem) => r.operatorName ?? r.operatorId ?? '—',
  },
  {
    title: '时间',
    dataIndex: 'createdAt',
    key: 'createdAt',
    width: 180,
    render: (v: string | null) => formatDateTime(v),
  },
];

export function TabSeatAddons({ tenantId, seatAddons }: Props) {
  const [addOpen, setAddOpen] = useState(false);

  return (
    <div>
      <Card size="small" style={{ marginBottom: 16 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <Statistic
            title="累计加购席位"
            value={seatAddons.totalQuantity}
            suffix="个"
            valueStyle={{ fontSize: 24 }}
          />
          <Button
            type="primary"
            icon={<PlusOutlined />}
            size="small"
            onClick={() => setAddOpen(true)}
          >
            加购席位
          </Button>
        </div>
      </Card>

      <Table<OpsSeatAddonHistoryItem>
        columns={columns}
        dataSource={seatAddons.history}
        rowKey="id"
        pagination={false}
        size="small"
        locale={{ emptyText: <Empty description="无加购记录" /> }}
      />

      <AddSeatAddonModal open={addOpen} tenantId={tenantId} onClose={() => setAddOpen(false)} />
    </div>
  );
}
