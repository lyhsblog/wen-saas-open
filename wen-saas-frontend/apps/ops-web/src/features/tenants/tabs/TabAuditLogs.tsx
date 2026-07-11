import { useState } from 'react';
import { App, Empty, Table, Typography } from 'antd';
import { CopyOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/lib/table';
import { useQuery } from '@tanstack/react-query';
import { formatDateTime, httpStatusBadge, StatusPill } from '@wen-saas/shared-ui';
import { opsTenantApi } from '@wen-saas/api-client';
import type { TenantAuditLogItem } from '@wen-saas/api-client';
import { http } from '../../../lib/http';

type MessageApi = ReturnType<typeof App.useApp>['message'];

const buildColumns = (message: MessageApi): ColumnsType<TenantAuditLogItem> => [
  {
    title: '时间',
    dataIndex: 'createdAt',
    key: 'createdAt',
    width: 180,
    render: (v: string | null) => formatDateTime(v),
  },
  {
    title: '操作',
    dataIndex: 'action',
    key: 'action',
    width: 160,
    render: (v: string) => v || '—',
  },
  {
    title: '对象类型',
    dataIndex: 'objectTypeLabel',
    key: 'objectTypeLabel',
    width: 120,
    render: (v: string | null) => v ?? '—',
  },
  {
    title: '对象',
    key: 'object',
    render: (_, row) => row.objectDesc ?? row.objectId ?? '—',
  },
  {
    title: '操作人',
    dataIndex: 'operatorName',
    key: 'operatorName',
    width: 140,
    render: (v: string | null) => v ?? '—',
  },
  {
    title: 'Request ID',
    dataIndex: 'requestId',
    key: 'requestId',
    width: 180,
    render: (v: string | null) => {
      if (!v) return '—';
      return (
        <Typography.Text
          style={{ fontFamily: 'var(--font-mono)', fontSize: 11, cursor: 'pointer' }}
          onClick={() => {
            void navigator.clipboard.writeText(v).then(() => {
              void message.success('已复制');
            });
          }}
        >
          <CopyOutlined style={{ marginRight: 4, opacity: 0.5 }} />
          {v.slice(0, 12)}…
        </Typography.Text>
      );
    },
  },
  {
    title: '状态码',
    dataIndex: 'statusCode',
    key: 'statusCode',
    width: 90,
    render: (v: number | null) => <StatusPill badge={httpStatusBadge(v)} />,
  },
];

type Props = {
  tenantId: string;
};

export function TabAuditLogs({ tenantId }: Props) {
  const { message } = App.useApp();
  const [page, setPage] = useState(1);
  const pageSize = 20;

  const { data, isLoading } = useQuery({
    queryKey: ['ops', 'tenant', tenantId, 'audit-logs', { page, pageSize }],
    queryFn: () => opsTenantApi(http).listTenantAuditLogs(tenantId, { page, pageSize }),
  });

  return (
    <Table<TenantAuditLogItem>
      columns={buildColumns(message)}
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
      locale={{ emptyText: <Empty description="暂无操作日志" /> }}
    />
  );
}
