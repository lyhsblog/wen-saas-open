import { useState } from 'react';
import {
  CopyableText,
  ErrorState,
  shouldSkipRowNav,
  StatusPill,
  toneBadge,
  useIsNarrow,
} from '@wen-saas/shared-ui';
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Drawer,
  Empty,
  Space,
  Spin,
  Table,
  Tooltip,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useCapabilities } from '@wen-saas/permissions';
import type { DataExportStatus, DataExportView } from '@wen-saas/api-client';
import { useDataExportDetail, useDataExports, useDownloadDataExport } from './complianceQueries';
import { SubmitDataExportModal } from './SubmitDataExportModal';
import { dataExportStatusBadge, dataModeBadge, fmtBytes, fmtTime } from './utils';

const PAGE_SIZE = 20;

/** 下载链接是否仍在有效期内。 */
function isExpired(expiresAt: string | null): boolean {
  if (!expiresAt) return false;
  const t = new Date(expiresAt).getTime();
  return !Number.isNaN(t) && t < Date.now();
}

/**
 * 客户全量数据导出（GDPR-style）：主管理员发起 + 申请台账 master-detail。
 * 仅主管理员可发起，月限 1 次；非主管理员仅查看（按钮禁用并提示）。
 * 宽屏（≥1100px）列表 + 右侧 420 详情面板并排；窄屏列表全宽、详情走 Drawer 覆盖。
 */
export function DataExportTab() {
  const isOwner = useCapabilities()?.tenantOwner ?? false;
  const narrow = useIsNarrow();
  const [page, setPage] = useState(1);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [submitOpen, setSubmitOpen] = useState(false);

  const { data, isLoading, isError, refetch } = useDataExports(page);

  const columns: ColumnsType<DataExportView> = [
    {
      title: '申请编号',
      dataIndex: 'requestNo',
      width: 160,
      render: (v: string) => <CopyableText value={v} />,
    },
    {
      title: '理由',
      dataIndex: 'reason',
      ellipsis: true,
    },
    {
      title: '敏感字段',
      dataIndex: 'dataMode',
      width: 90,
      render: (v: 'FULL' | 'MASKED') => <StatusPill badge={dataModeBadge(v)} />,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (v: DataExportStatus) => <StatusPill badge={dataExportStatusBadge(v)} />,
    },
    {
      title: '发起人',
      dataIndex: 'applicantName',
      width: 110,
      render: (v: string | null) => v ?? '—',
    },
    {
      title: '发起时间',
      dataIndex: 'requestedAt',
      width: 170,
      render: (v: string | null) => fmtTime(v),
    },
  ];

  const submitBtn = (
    <Button type="primary" disabled={!isOwner} onClick={() => setSubmitOpen(true)}>
      发起全量数据导出
    </Button>
  );

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <Alert
        type="warning"
        showIcon
        message="敏感操作"
        description="客户全量数据导出仅主管理员可发起，必填理由、每月限 1 次，全过程记录到操作日志。彻底销户不在产品自助层面提供，需走运营线下流程。"
      />

      <Card
        size="small"
        title="全量导出申请"
        extra={
          isOwner ? submitBtn : <Tooltip title="仅主管理员可发起全量数据导出">{submitBtn}</Tooltip>
        }
      >
        {isError ? (
          <ErrorState
            title="申请列表加载失败"
            description="请稍后重试；若持续失败请联系管理员。"
            primaryAction={{ label: '重试', onClick: () => void refetch() }}
          />
        ) : (
          <div style={{ display: 'flex', gap: 16 }}>
            <div style={{ flex: 1, minWidth: 0 }}>
              <Table<DataExportView>
                rowKey="id"
                size="small"
                columns={columns}
                dataSource={data?.items}
                loading={isLoading}
                onRow={(row) => ({
                  onClick: () => {
                    if (shouldSkipRowNav()) return;
                    setSelectedId(row.id);
                  },
                  style: { cursor: 'pointer' },
                })}
                rowClassName={(row) => (row.id === selectedId ? 'ant-table-row-selected' : '')}
                pagination={{
                  current: page,
                  pageSize: PAGE_SIZE,
                  total: data?.total ?? 0,
                  showSizeChanger: false,
                  showTotal: (n) => `共 ${n} 条`,
                  onChange: (p) => setPage(p),
                }}
              />
            </div>
            {selectedId && !narrow && (
              <div style={{ width: 420, flexShrink: 0 }}>
                <DataExportDetail id={selectedId} onClose={() => setSelectedId(null)} />
              </div>
            )}
          </div>
        )}
      </Card>

      {selectedId && narrow && (
        <Drawer
          open
          title="导出详情"
          width="min(420px, 100vw)"
          onClose={() => setSelectedId(null)}
          destroyOnHidden
        >
          <DataExportDetail id={selectedId} onClose={() => setSelectedId(null)} plain />
        </Drawer>
      )}

      <SubmitDataExportModal
        open={submitOpen}
        onCancel={() => setSubmitOpen(false)}
        onSubmitted={(created) => {
          setSubmitOpen(false);
          setPage(1);
          setSelectedId(created.id);
        }}
      />
    </div>
  );
}

function DataExportDetail({
  id,
  onClose,
  plain = false,
}: {
  id: string;
  onClose: () => void;
  /** 窄屏放进 Drawer 时去掉 Card 外壳（标题/关闭由 Drawer 承担），避免双重边框。 */
  plain?: boolean;
}) {
  const { data, isLoading } = useDataExportDetail(id);
  const download = useDownloadDataExport();

  const body = !data ? (
    <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} />
  ) : (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Descriptions column={1} size="small" bordered>
        <Descriptions.Item label="申请编号">{data.requestNo}</Descriptions.Item>
        <Descriptions.Item label="状态">
          <StatusPill badge={dataExportStatusBadge(data.status)} />
        </Descriptions.Item>
        <Descriptions.Item label="敏感字段">
          <StatusPill badge={dataModeBadge(data.dataMode)} />
        </Descriptions.Item>
        <Descriptions.Item label="申请理由">{data.reason}</Descriptions.Item>
        <Descriptions.Item label="导出范围">{data.scopeSummary ?? '—'}</Descriptions.Item>
        <Descriptions.Item label="发起人">{data.applicantName ?? '—'}</Descriptions.Item>
        <Descriptions.Item label="发起时间">{fmtTime(data.requestedAt)}</Descriptions.Item>
        {data.status === 'COMPLETED' && (
          <>
            <Descriptions.Item label="文件">{data.fileName ?? '—'}</Descriptions.Item>
            <Descriptions.Item label="大小">{fmtBytes(data.fileSize)}</Descriptions.Item>
            <Descriptions.Item label="完成时间">{fmtTime(data.completedAt)}</Descriptions.Item>
            <Descriptions.Item label="下载有效期">
              {fmtTime(data.expiresAt)}
              {isExpired(data.expiresAt) && (
                <span style={{ marginLeft: 8 }}>
                  <StatusPill badge={toneBadge('danger', '已过期')} />
                </span>
              )}
            </Descriptions.Item>
          </>
        )}
        {data.status === 'FAILED' && (
          <Descriptions.Item label="失败原因">{data.errorMessage ?? '—'}</Descriptions.Item>
        )}
      </Descriptions>

      {data.status === 'COMPLETED' &&
        (isExpired(data.expiresAt) ? (
          <Tooltip title="下载链接已过期，请重新发起">
            <Button type="primary" block disabled>
              下载导出包
            </Button>
          </Tooltip>
        ) : (
          <Button
            type="primary"
            block
            loading={download.isPending}
            onClick={() => download.mutate(data.id)}
          >
            下载导出包
          </Button>
        ))}
      {data.status === 'PACKAGING' && (
        <Alert type="info" showIcon message="正在打包，完成后将站内信通知主管理员，可在此处下载" />
      )}
    </Space>
  );

  if (plain) {
    return isLoading ? (
      <div style={{ padding: 48, textAlign: 'center' }}>
        <Spin />
      </div>
    ) : (
      body
    );
  }

  return (
    <Card
      size="small"
      title="导出详情"
      extra={
        <Button type="text" size="small" onClick={onClose}>
          关闭
        </Button>
      }
      loading={isLoading}
    >
      {body}
    </Card>
  );
}
