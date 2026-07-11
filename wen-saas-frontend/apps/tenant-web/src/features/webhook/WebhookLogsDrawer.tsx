import { useState } from 'react';
import { Drawer, Select, Table, Tooltip } from 'antd';
import { formatDateTime, StatusPill, toneBadge, type SemanticTone } from '@wen-saas/shared-ui';
import type { WebhookLogItem, WebhookView } from '@wen-saas/api-client';
import { useWebhookLogs } from './webhookQueries';

const STATUS_META: Record<string, { tone: SemanticTone; label: string }> = {
  success: { tone: 'success', label: '成功' },
  failed: { tone: 'warning', label: '失败(将重试)' },
  abandoned: { tone: 'danger', label: '已放弃' },
};

/** 投递日志：按订阅分页倒序，逐次尝试一行（同一事件的重试各占一行）。 */
export function WebhookLogsDrawer({
  webhook,
  onClose,
}: {
  webhook: WebhookView | null;
  onClose: () => void;
}) {
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(20);
  const [finalStatus, setFinalStatus] = useState<string | undefined>(undefined);
  const query = useWebhookLogs(webhook?.id ?? null, page, size, finalStatus);
  const records = query.data?.records ?? [];
  const total = Number(query.data?.total ?? 0);

  return (
    <Drawer
      title={webhook ? `投递日志 · ${webhook.name}` : '投递日志'}
      width={760}
      open={!!webhook}
      onClose={onClose}
      destroyOnHidden
    >
      <div style={{ marginBottom: 12 }}>
        <Select
          allowClear
          placeholder="终态筛选"
          style={{ width: 160 }}
          {...(finalStatus ? { value: finalStatus } : {})}
          onChange={(v) => {
            setFinalStatus(v ?? undefined);
            setPage(1);
          }}
          options={[
            { value: 'success', label: '成功' },
            { value: 'failed', label: '失败(将重试)' },
            { value: 'abandoned', label: '已放弃' },
          ]}
        />
      </div>
      <Table<WebhookLogItem>
        rowKey="id"
        size="small"
        loading={query.isLoading}
        dataSource={records}
        pagination={{
          current: page,
          pageSize: size,
          total,
          showSizeChanger: true,
          showTotal: (n) => `共 ${n} 条`,
          onChange: (p, s) => {
            setPage(p);
            setSize(s);
          },
        }}
        columns={[
          {
            title: '时间',
            dataIndex: 'createdAt',
            width: 160,
            render: (v: string) => formatDateTime(v),
          },
          { title: '事件', dataIndex: 'eventType', width: 220 },
          {
            title: 'HTTP',
            dataIndex: 'httpStatus',
            width: 70,
            render: (v: number | null) => v ?? '-',
          },
          {
            title: '耗时',
            dataIndex: 'durationMs',
            width: 80,
            render: (v: number | null) => (v == null ? '-' : `${v}ms`),
          },
          { title: '重试', dataIndex: 'retryCount', width: 60 },
          {
            title: '结果',
            dataIndex: 'finalStatus',
            width: 110,
            render: (v: string) => {
              const meta = STATUS_META[v];
              return <StatusPill badge={toneBadge(meta?.tone ?? 'neutral', meta?.label ?? v)} />;
            },
          },
          {
            title: '错误',
            dataIndex: 'errorMessage',
            ellipsis: true,
            render: (v: string | null, r) => {
              const text = v ?? '';
              if (!text) return '-';
              const detail = r.responseBody ? `${text}\n响应：${r.responseBody}` : text;
              return (
                <Tooltip title={<pre style={{ margin: 0, whiteSpace: 'pre-wrap' }}>{detail}</pre>}>
                  <span>{text}</span>
                </Tooltip>
              );
            },
          },
        ]}
      />
    </Drawer>
  );
}
