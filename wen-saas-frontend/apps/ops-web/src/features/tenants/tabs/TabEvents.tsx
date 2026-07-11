import { useState } from 'react';
import { Empty, Table } from 'antd';
import type { ColumnsType } from 'antd/lib/table';
import { useQuery } from '@tanstack/react-query';
import { formatDateTime } from '@wen-saas/shared-ui';
import { opsTenantApi } from '@wen-saas/api-client';
import type { TenantEventItem } from '@wen-saas/api-client';
import { http } from '../../../lib/http';

const ACTION_LABEL: Record<string, string> = {
  create_tenant: '开通租户',
  assign_plan: '勾选套餐',
  renew_plan: '续费套餐',
  revoke_plan: '取消套餐',
  add_seat_addon: '加购席位',
  update_status: '更新状态',
  update_name: '修改名称',
  update_remark: '编辑备注',
  transfer_main_admin: '更换主管理员',
  reissue_reset_link: '重发密码链接',
  unlock_login: '解除登录锁定',
  impersonate: '代登录',
};

const columns: ColumnsType<TenantEventItem> = [
  {
    title: '时间',
    dataIndex: 'createdAt',
    key: 'createdAt',
    width: 180,
    render: (v: string | null) => formatDateTime(v),
  },
  {
    title: '事件',
    dataIndex: 'action',
    key: 'action',
    width: 160,
    render: (v: string) => ACTION_LABEL[v] ?? v,
  },
  {
    title: '对象',
    key: 'object',
    render: (_, row) =>
      row.objectDesc ?? (row.objectId ? `${row.objectType ?? ''} ${row.objectId}` : '—'),
  },
  {
    title: '操作人',
    dataIndex: 'operatorName',
    key: 'operatorName',
    width: 140,
    render: (v: string | null) => v ?? '—',
  },
];

type Props = {
  tenantId: string;
};

export function TabEvents({ tenantId }: Props) {
  const [page, setPage] = useState(1);
  const pageSize = 20;

  const { data, isLoading } = useQuery({
    queryKey: ['ops', 'tenant', tenantId, 'events', { page, pageSize }],
    queryFn: () => opsTenantApi(http).listTenantEvents(tenantId, { page, pageSize }),
  });

  return (
    <Table<TenantEventItem>
      columns={columns}
      dataSource={data?.items}
      rowKey="id"
      loading={isLoading}
      size="small"
      pagination={{
        current: page,
        pageSize,
        total: data?.total ?? 0,
        showTotal: (n) => `共 ${n} 条`,
        onChange: (p) => setPage(p),
        showSizeChanger: false,
      }}
      locale={{ emptyText: <Empty description="暂无变更历史" /> }}
    />
  );
}
