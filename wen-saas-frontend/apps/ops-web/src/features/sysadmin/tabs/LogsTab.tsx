import { useState } from 'react';
import {
  CopyableText,
  CountChip,
  formatDateTime,
  shouldSkipRowNav,
  StatusPill,
} from '@wen-saas/shared-ui';
import { Button, DatePicker, Descriptions, Drawer, Input, Space, Table } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useQuery } from '@tanstack/react-query';
import { opsOperationLogApi, type OpLogListItem } from '@wen-saas/api-client';
import { http } from '../../../lib/http';
import { opLogStatusBadge } from '../utils/sysadminMeta';
import { DataChangesView } from '../../../components/DataChangesView';

const { RangePicker } = DatePicker;

type Filters = {
  startAt?: string | undefined;
  endAt?: string | undefined;
  action?: string | undefined;
  targetTenantId?: string | undefined;
  requestId?: string | undefined;
};

export function LogsTab() {
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(20);
  const [filters, setFilters] = useState<Filters>({});
  const [selected, setSelected] = useState<OpLogListItem | null>(null);

  const query = { page, size, ...filters };
  const { data, isLoading } = useQuery({
    queryKey: ['ops', 'op-logs', query],
    queryFn: () => opsOperationLogApi(http).listLogs(query),
  });

  const { data: detail, isLoading: detailLoading } = useQuery({
    queryKey: ['ops', 'op-log', selected?.id, selected?.createdAt],
    queryFn: () => opsOperationLogApi(http).getDetail(selected!.id, selected!.createdAt),
    enabled: selected != null,
  });

  const patch = (f: Partial<Filters>) => {
    setPage(1);
    setFilters((prev) => ({ ...prev, ...f }));
  };

  const columns: ColumnsType<OpLogListItem> = [
    { title: '时间', dataIndex: 'createdAt', width: 170, render: (v: string) => formatDateTime(v) },
    {
      title: '操作人',
      dataIndex: 'operatorName',
      width: 130,
      render: (name: string | null) =>
        name ?? <span style={{ color: 'var(--text-tertiary)' }}>系统</span>,
    },
    {
      title: '操作类型',
      dataIndex: 'action',
      width: 130,
      render: (a: string) => <CountChip>{a}</CountChip>,
    },
    {
      title: '目标租户',
      dataIndex: 'targetTenantName',
      width: 160,
      render: (v: string | null) => v ?? <span style={{ color: 'var(--text-tertiary)' }}>—</span>,
    },
    {
      title: '对象类型',
      dataIndex: 'objectTypeLabel',
      width: 120,
      render: (v: string | null) => v ?? '—',
    },
    {
      title: '详情',
      key: 'desc',
      render: (_v, row) =>
        row.objectDesc ?? <span style={{ color: 'var(--text-tertiary)' }}>—</span>,
    },
    {
      title: '状态',
      dataIndex: 'statusCode',
      width: 90,
      render: (code: number | null) => {
        const s = opLogStatusBadge(code);
        return s ? <StatusPill badge={s} /> : '—';
      },
    },
    {
      title: 'Request ID',
      dataIndex: 'requestId',
      width: 160,
      render: (v: string | null) => (
        <CopyableText value={v} style={{ fontSize: 11, color: 'var(--text-tertiary)' }} />
      ),
    },
  ];

  return (
    <div style={{ padding: 24, display: 'flex', flexDirection: 'column', gap: 12 }}>
      <Space wrap>
        <RangePicker
          showTime
          onChange={(range) =>
            patch({
              startAt: range?.[0] ? range[0].toISOString() : undefined,
              endAt: range?.[1] ? range[1].toISOString() : undefined,
            })
          }
        />
        <Input
          allowClear
          placeholder="操作类型，如 更新"
          style={{ width: 160 }}
          onChange={(e) => patch({ action: e.target.value || undefined })}
        />
        <Input
          allowClear
          placeholder="目标租户 ID"
          style={{ width: 160 }}
          onChange={(e) => patch({ targetTenantId: e.target.value || undefined })}
        />
        <Input.Search
          allowClear
          placeholder="Request ID"
          style={{ width: 220 }}
          onSearch={(v) => patch({ requestId: v || undefined })}
        />
        <Button
          onClick={() => {
            setFilters({});
            setPage(1);
          }}
        >
          重置
        </Button>
      </Space>

      <Table
        rowKey="id"
        size="middle"
        loading={isLoading}
        columns={columns}
        dataSource={data?.records ?? []}
        onRow={(row) => ({
          onClick: () => {
            if (shouldSkipRowNav()) return;
            setSelected(row);
          },
          style: { cursor: 'pointer' },
        })}
        pagination={{
          current: page,
          pageSize: size,
          total: data?.total ?? 0,
          showSizeChanger: true,
          showTotal: (t) => `共 ${t} 条`,
          onChange: (p, s) => {
            setPage(p);
            setSize(s);
          },
        }}
      />

      <Drawer
        open={selected != null}
        title="操作日志详情"
        width={560}
        onClose={() => setSelected(null)}
        loading={detailLoading}
      >
        {detail && (
          <>
            <Descriptions column={1} size="small" bordered>
              <Descriptions.Item label="时间">{formatDateTime(detail.createdAt)}</Descriptions.Item>
              <Descriptions.Item label="操作人">
                {detail.operatorName ?? '系统'}（{detail.operatorId ?? '—'}）
              </Descriptions.Item>
              <Descriptions.Item label="操作类型">
                <CountChip>{detail.action}</CountChip>
              </Descriptions.Item>
              <Descriptions.Item label="模块">{detail.module ?? '—'}</Descriptions.Item>
              <Descriptions.Item label="对象类型">
                {detail.objectTypeLabel ?? '—'}
              </Descriptions.Item>
              <Descriptions.Item label="对象">{detail.objectDesc ?? '—'}</Descriptions.Item>
              <Descriptions.Item label="目标租户">
                {detail.targetTenantName ?? '—'}
              </Descriptions.Item>
              <Descriptions.Item label="请求">
                {detail.method || detail.path
                  ? `${detail.method ?? ''} ${detail.path ?? ''}`.trim()
                  : '—'}
              </Descriptions.Item>
              <Descriptions.Item label="状态">
                {(() => {
                  const s = opLogStatusBadge(detail.statusCode);
                  if (!s) return '—';
                  return (
                    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                      <StatusPill badge={s} />
                      <span style={{ fontSize: 12, color: 'var(--text-tertiary)' }}>
                        HTTP {detail.statusCode}
                      </span>
                    </span>
                  );
                })()}
              </Descriptions.Item>
              <Descriptions.Item label="耗时">
                {detail.durationMs != null ? `${detail.durationMs} ms` : '—'}
              </Descriptions.Item>
              <Descriptions.Item label="IP">{detail.ip ?? '—'}</Descriptions.Item>
              <Descriptions.Item label="Request ID">
                <CopyableText value={detail.requestId} />
              </Descriptions.Item>
              <Descriptions.Item label="User-Agent">
                <span
                  style={{ fontSize: 12, color: 'var(--text-secondary)', wordBreak: 'break-all' }}
                >
                  {detail.userAgent ?? '—'}
                </span>
              </Descriptions.Item>
              {detail.errorMessage && (
                <Descriptions.Item label="错误">{detail.errorMessage}</Descriptions.Item>
              )}
            </Descriptions>
            <DataChangesView items={detail.dataChanges} />
          </>
        )}
      </Drawer>
    </div>
  );
}
