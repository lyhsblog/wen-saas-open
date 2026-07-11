import { useState } from 'react';
import { formatDateTime, shouldSkipRowNav, StatusPill, toneBadge } from '@wen-saas/shared-ui';
import { Alert, Empty, Modal, Spin, Table } from 'antd';
import type { ColumnsType } from 'antd/lib/table';
import { useQuery } from '@tanstack/react-query';
import { opsOperationLogApi, opsPackageAdminApi } from '@wen-saas/api-client';
import type { PackageHistoryItemView } from '@wen-saas/api-client';
import { http } from '../../../lib/http';
import { DataChangesView } from '../../../components/DataChangesView';

const ACTION_LABEL: Record<string, string> = {
  create: '创建',
  update: '编辑',
  publish: '上架',
  unpublish: '下架',
  archive: '归档',
  delete: '删除',
  copy: '复制',
};

type Props = {
  code: string;
};

export function TabPackageHistory({ code }: Props) {
  const [page, setPage] = useState(1);
  const [detailKey, setDetailKey] = useState<{ id: string; createdAt: string } | null>(null);
  const pageSize = 20;

  const { data, isLoading } = useQuery({
    queryKey: ['ops', 'package', code, 'history', { page, pageSize }],
    queryFn: () => opsPackageAdminApi(http).listPackageHistory(code, { page, size: pageSize }),
  });

  const { data: detail, isLoading: detailLoading } = useQuery({
    queryKey: ['ops', 'operation-log', detailKey?.id, detailKey?.createdAt],
    queryFn: () => opsOperationLogApi(http).getDetail(detailKey!.id, detailKey!.createdAt),
    enabled: detailKey != null,
  });

  const columns: ColumnsType<PackageHistoryItemView> = [
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
      width: 100,
      render: (v: string) => ACTION_LABEL[v] ?? v,
    },
    {
      title: '对象',
      dataIndex: 'objectDesc',
      key: 'objectDesc',
      render: (v: string | null) => v ?? '—',
    },
    {
      title: '操作人',
      dataIndex: 'operatorName',
      key: 'operatorName',
      width: 140,
      render: (v: string | null) => v ?? '—',
    },
    {
      title: '结果',
      dataIndex: 'statusCode',
      key: 'statusCode',
      width: 80,
      render: (v: number | null) =>
        v == null ? (
          '—'
        ) : (
          <StatusPill
            badge={v < 400 ? toneBadge('success', '成功') : toneBadge('danger', '失败')}
          />
        ),
    },
  ];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      <Alert
        type="info"
        showIcon
        message="变更历史为异步落库，刚发生的操作可能有秒级延迟。点击行可查看改前 / 改后明细。"
      />
      <Table<PackageHistoryItemView>
        columns={columns}
        dataSource={data?.items}
        rowKey="id"
        loading={isLoading}
        size="small"
        onRow={(row) => ({
          style: { cursor: row.createdAt ? 'pointer' : 'default' },
          onClick: () => {
            if (shouldSkipRowNav()) return;
            if (row.createdAt) setDetailKey({ id: row.id, createdAt: row.createdAt });
          },
        })}
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
      <Modal
        open={detailKey != null}
        title="变更明细"
        footer={null}
        onCancel={() => setDetailKey(null)}
        width={680}
        destroyOnHidden
      >
        {detailLoading || !detail ? (
          <div style={{ padding: 48, textAlign: 'center' }}>
            <Spin />
          </div>
        ) : (
          <DataChangesView items={detail.dataChanges} title="变更明细" />
        )}
      </Modal>
    </div>
  );
}
