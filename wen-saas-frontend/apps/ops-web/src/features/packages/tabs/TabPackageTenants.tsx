import { useState } from 'react';
import { Empty, Input, Select, Table } from 'antd';
import { CopyableText, formatDate } from '@wen-saas/shared-ui';
import type { ColumnsType } from 'antd/lib/table';
import { useQuery } from '@tanstack/react-query';
import { opsPackageAdminApi } from '@wen-saas/api-client';
import type { TenantListItemView } from '@wen-saas/api-client';
import { http } from '../../../lib/http';
import { StatusPill } from '@wen-saas/shared-ui';
import { statusBadgeOf } from '../../tenants/utils/statusOf';

const STATUS_OPTIONS: { label: string; value: 0 | 1 | 2 }[] = [
  { label: '正常', value: 1 },
  { label: '暂停', value: 0 },
  { label: '已过期', value: 2 },
];

const columns: ColumnsType<TenantListItemView> = [
  {
    title: '租户',
    key: 'name',
    render: (_, row) => (
      <div style={{ display: 'flex', flexDirection: 'column' }}>
        <span style={{ fontWeight: 500 }}>{row.name}</span>
        <code
          style={{ fontFamily: 'var(--font-mono)', fontSize: 12, color: 'var(--text-tertiary)' }}
        >
          {row.code}
        </code>
      </div>
    ),
  },
  {
    title: '内部编号',
    dataIndex: 'tenantNo',
    key: 'tenantNo',
    width: 120,
    render: (v: string | null) => <CopyableText value={v} />,
  },
  {
    title: '主管理员',
    dataIndex: 'adminUsername',
    key: 'adminUsername',
    width: 140,
    render: (v: string | null) => v ?? '—',
  },
  {
    title: '客户经理',
    dataIndex: 'csmName',
    key: 'csmName',
    width: 120,
    render: (v: string | null) => v ?? '—',
  },
  {
    title: '状态',
    key: 'status',
    width: 100,
    render: (_, row) => <StatusPill badge={statusBadgeOf(row)} />,
  },
  {
    title: '最近到期',
    dataIndex: 'nearestExpireAt',
    key: 'nearestExpireAt',
    width: 120,
    render: (v: string | null) => (v ? formatDate(v) : '永久'),
  },
];

type Props = {
  code: string;
};

export function TabPackageTenants({ code }: Props) {
  const [page, setPage] = useState(1);
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<0 | 1 | 2 | undefined>(undefined);
  const pageSize = 20;

  const { data, isLoading } = useQuery({
    queryKey: ['ops', 'package', code, 'tenants', { page, pageSize, keyword, status }],
    queryFn: () =>
      opsPackageAdminApi(http).listPackageTenants(code, {
        page,
        pageSize,
        ...(keyword ? { keyword } : {}),
        ...(status != null ? { status } : {}),
      }),
  });

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
        <Input.Search
          size="middle"
          placeholder="搜索租户名 / 标识"
          allowClear
          style={{ width: 240 }}
          onSearch={(v) => {
            setPage(1);
            setKeyword(v);
          }}
        />
        <Select<0 | 1 | 2>
          size="middle"
          style={{ width: 120 }}
          placeholder="全部状态"
          allowClear
          value={status ?? null}
          onChange={(v) => {
            setPage(1);
            setStatus(v ?? undefined);
          }}
          options={STATUS_OPTIONS}
        />
      </div>
      <Table<TenantListItemView>
        columns={columns}
        dataSource={data?.items}
        rowKey="id"
        loading={isLoading}
        size="small"
        pagination={{
          current: page,
          pageSize,
          total: data?.total ?? 0,
          showTotal: (n) => `共 ${n} 家`,
          onChange: (p) => setPage(p),
          showSizeChanger: false,
        }}
        locale={{ emptyText: <Empty description="暂无租户勾选此套餐" /> }}
      />
    </div>
  );
}
