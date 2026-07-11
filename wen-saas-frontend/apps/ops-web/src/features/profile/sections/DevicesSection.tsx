import { useState } from 'react';
import { Button, Popconfirm, Space } from 'antd';
import type { ColumnsType } from 'antd/lib/table';
import { CountChip, DataTable, formatDateTime, StatusPill, toneBadge } from '@wen-saas/shared-ui';
import type { OpsLoginLogItem, OpsSessionItem } from '@wen-saas/api-client';
import { SectionCard } from '../components/SectionCard';
import {
  useLoginLogsQuery,
  useRevokeOtherSessions,
  useRevokeSession,
  useSessionsQuery,
} from '../profileQueries';
import { LOGIN_METHOD_LABEL, LOGIN_RESULT_LABEL } from '../constants';

function shortUa(ua: string | undefined): string {
  if (!ua) return '—';
  if (/iPhone|iPad|iOS/i.test(ua)) return 'iOS · Safari';
  if (/Android/i.test(ua)) return 'Android';
  if (/Mac OS X/i.test(ua)) return 'macOS · Safari';
  if (/Windows/i.test(ua)) return 'Windows · Chrome';
  return ua.split(' ')[0] ?? '—';
}

export function DevicesSection() {
  const sessions = useSessionsQuery();
  const revokeOne = useRevokeSession();
  const revokeOthers = useRevokeOtherSessions();
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const logs = useLoginLogsQuery(page, pageSize);

  const sessionColumns: ColumnsType<OpsSessionItem> = [
    {
      title: '设备',
      dataIndex: 'deviceLabel',
      width: 200,
      render: (label: string | undefined, row) => (
        <Space>
          <span>{label ?? '未知设备'}</span>
          {row.current ? <CountChip tone="brand">当前</CountChip> : null}
        </Space>
      ),
    },
    {
      title: 'IP',
      dataIndex: 'ip',
      width: 140,
      render: (v: string | undefined) => (
        <span style={{ fontFamily: 'var(--font-mono, ui-monospace, monospace)' }}>{v ?? '—'}</span>
      ),
    },
    { title: '登录时间', dataIndex: 'loginAt', width: 180, render: formatDateTime },
    { title: '最近活跃', dataIndex: 'lastAccessAt', width: 180, render: formatDateTime },
    {
      title: '操作',
      key: 'op',
      width: 100,
      render: (_, row) =>
        row.current ? (
          <Button size="small" disabled title="当前会话不可下线">
            下线
          </Button>
        ) : (
          <Popconfirm
            title="下线该设备"
            description="对应设备将立即被踢出登录"
            okText="下线"
            cancelText="取消"
            onConfirm={() => row.sessionId && revokeOne.mutate(row.sessionId)}
          >
            <Button size="small" danger type="text">
              下线
            </Button>
          </Popconfirm>
        ),
    },
  ];

  const logColumns: ColumnsType<OpsLoginLogItem> = [
    { title: '时间', dataIndex: 'createdAt', width: 180, render: formatDateTime },
    {
      title: '账号',
      dataIndex: 'account',
      width: 180,
      render: (v: string | undefined) => (
        <span style={{ fontFamily: 'var(--font-mono, ui-monospace, monospace)' }}>{v ?? '—'}</span>
      ),
    },
    {
      title: '方式',
      dataIndex: 'loginMethod',
      width: 80,
      render: (v: string | undefined) => LOGIN_METHOD_LABEL[v ?? ''] ?? v ?? '—',
    },
    { title: '动作', dataIndex: 'action', width: 80 },
    {
      title: '结果',
      dataIndex: 'result',
      width: 90,
      render: (v: string | undefined) => {
        const meta = LOGIN_RESULT_LABEL[v ?? ''];
        if (!meta) return v ?? '—';
        return <StatusPill badge={toneBadge(meta.tone, meta.text)} />;
      },
    },
    { title: '失败原因', dataIndex: 'failReason' },
    { title: 'IP', dataIndex: 'ip', width: 140 },
    {
      title: '设备',
      dataIndex: 'userAgent',
      width: 140,
      render: (v: string | undefined) => shortUa(v),
    },
  ];

  return (
    <SectionCard
      id="devices"
      title="登录设备与会话"
      extra={
        <Popconfirm
          title="下线其他设备"
          description="除当前设备外，其他设备将立即被踢出登录"
          okText="全部下线"
          cancelText="取消"
          onConfirm={() => revokeOthers.mutate()}
        >
          <Button size="small">下线其他设备</Button>
        </Popconfirm>
      }
    >
      <DataTable<OpsSessionItem>
        columns={sessionColumns}
        dataSource={sessions.data ?? []}
        loading={sessions.isLoading}
        rowKey={(r) => r.sessionId ?? ''}
        pagination={false}
        density="compact"
      />

      <div style={{ marginTop: 24, fontSize: 13, fontWeight: 600, marginBottom: 8 }}>登录日志</div>
      <DataTable<OpsLoginLogItem>
        columns={logColumns}
        dataSource={logs.data?.records ?? []}
        loading={logs.isLoading}
        rowKey={(r) => r.id ?? ''}
        density="compact"
        pagination={{
          current: page,
          pageSize,
          total: logs.data?.total ?? 0,
          showSizeChanger: true,
          pageSizeOptions: [10, 20, 50],
          onChange: (p, ps) => {
            setPage(p);
            setPageSize(ps);
          },
        }}
      />
    </SectionCard>
  );
}
