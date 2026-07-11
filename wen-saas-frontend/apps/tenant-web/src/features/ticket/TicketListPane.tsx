import { useState } from 'react';
import { CopyableText, CountChip, StatusPill, shouldSkipRowNav } from '@wen-saas/shared-ui';
import { Button, Empty, Input, Spin, Table } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { PlusOutlined } from '@ant-design/icons';
import { useHasPermission } from '@wen-saas/permissions';
import type { TicketStatus, TicketSummary } from '@wen-saas/api-client';
import { useAllTickets, useMyTickets } from './ticketQueries';
import {
  fromNow,
  STATUS_TABS,
  TYPE_LABEL,
  ticketPriorityBadge,
  ticketSlaBadge,
  ticketStatusBadge,
} from './utils/ticketMeta';

type Scope = 'mine' | 'all';

type Props = {
  selectedId: string | null;
  onSelect: (id: string) => void;
  onSubmit: () => void;
  compact: boolean;
};

const PAGE_SIZE = 20;

export function TicketListPane({ selectedId, onSelect, onSubmit, compact }: Props) {
  const canViewAll = useHasPermission('base.ticket.view_all');
  const [scope, setScope] = useState<Scope>('mine');
  const [tab, setTab] = useState<TicketStatus | 'all'>('all');
  const [keyword, setKeyword] = useState('');
  const [page, setPage] = useState(1);

  const statusParam = tab !== 'all' ? { status: tab } : {};
  const isAll = scope === 'all' && canViewAll;

  const mine = useMyTickets(
    isAll ? { page: 1, size: 1 } : { ...statusParam, page, size: PAGE_SIZE },
  );
  const all = useAllTickets(
    { ...statusParam, ...(keyword ? { keyword } : {}), page, size: PAGE_SIZE },
    isAll,
  );
  const active = isAll ? all : mine;
  const data = active.data;
  const isLoading = active.isLoading;

  const resetPage = () => setPage(1);

  const columns: ColumnsType<TicketSummary> = [
    {
      title: '工单',
      dataIndex: 'title',
      key: 'title',
      render: (_: unknown, r) => (
        <div style={{ minWidth: 0 }}>
          <div style={{ fontWeight: 600, fontSize: 14 }}>{r.title}</div>
          <div style={{ fontSize: 12, color: 'var(--text-tertiary)' }}>
            <CopyableText
              value={r.ticketNo}
              style={{ fontFamily: 'var(--font-mono, monospace)' }}
            />
            <span> · {TYPE_LABEL[r.type]}</span>
            {isAll && r.submitterName && <span> · {r.submitterName}</span>}
          </div>
        </div>
      ),
    },
    {
      title: '优先级',
      dataIndex: 'priority',
      key: 'priority',
      width: 80,
      render: (_: unknown, r) => <StatusPill badge={ticketPriorityBadge(r.priority)} />,
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 140,
      render: (_: unknown, r) => (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 4, alignItems: 'flex-start' }}>
          <StatusPill badge={ticketStatusBadge(r.status)} />
          {(r.status === 'PENDING' || r.status === 'PROCESSING') && r.slaState !== 'normal' && (
            <StatusPill badge={ticketSlaBadge(r.slaState)} />
          )}
        </div>
      ),
    },
    {
      title: '最近活动',
      dataIndex: 'lastActivityAt',
      key: 'lastActivity',
      width: 96,
      render: (_: unknown, r) => (
        <span style={{ fontSize: 12, color: 'var(--text-secondary)' }}>
          {fromNow(r.lastActivityAt)}
        </span>
      ),
    },
  ];

  return (
    <div
      style={{
        flex: compact ? undefined : 1,
        width: compact ? 560 : undefined,
        flexShrink: compact ? 0 : undefined,
        display: 'flex',
        flexDirection: 'column',
        overflow: 'hidden',
      }}
    >
      <div
        style={{
          padding: '14px 24px',
          borderBottom: '1px solid var(--divider)',
          display: 'flex',
          flexDirection: 'column',
          gap: 12,
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <span style={{ fontWeight: 600, fontSize: 15 }}>工单</span>
          {data && <CountChip>{data.total}</CountChip>}
          <div style={{ flex: 1 }} />
          <Button type="primary" icon={<PlusOutlined />} onClick={onSubmit}>
            提交工单
          </Button>
        </div>

        {canViewAll && (
          <div style={{ display: 'flex', gap: 4 }}>
            {(['mine', 'all'] as const).map((sc) => {
              const activeSc = scope === sc;
              return (
                <button
                  key={sc}
                  type="button"
                  onClick={() => {
                    setScope(sc);
                    resetPage();
                  }}
                  style={{
                    padding: '4px 14px',
                    fontSize: 13,
                    cursor: 'pointer',
                    borderRadius: 'var(--r-sm, 4px)',
                    border: '1px solid',
                    borderColor: activeSc ? 'var(--brand)' : 'var(--border)',
                    background: activeSc ? 'var(--brand-bg)' : 'transparent',
                    color: activeSc ? 'var(--brand)' : 'var(--text-secondary)',
                    fontWeight: activeSc ? 600 : 400,
                  }}
                >
                  {sc === 'mine' ? '我的工单' : '全部工单'}
                </button>
              );
            })}
          </div>
        )}

        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'center' }}>
          <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap' }}>
            {STATUS_TABS.map((t) => {
              const activeTab = tab === t.key;
              return (
                <button
                  key={t.key}
                  type="button"
                  onClick={() => {
                    setTab(t.key);
                    resetPage();
                  }}
                  style={{
                    padding: '4px 12px',
                    fontSize: 13,
                    cursor: 'pointer',
                    borderRadius: 'var(--r-sm, 4px)',
                    border: '1px solid',
                    borderColor: activeTab ? 'var(--brand)' : 'var(--border)',
                    background: activeTab ? 'var(--brand-bg)' : 'transparent',
                    color: activeTab ? 'var(--brand)' : 'var(--text-secondary)',
                    fontWeight: activeTab ? 600 : 400,
                  }}
                >
                  {t.label}
                </button>
              );
            })}
          </div>
          <div style={{ flex: 1 }} />
          {isAll && (
            <Input.Search
              size="small"
              placeholder="搜索标题 / 工单号"
              allowClear
              style={{ width: 220 }}
              onSearch={(v) => {
                setKeyword(v);
                resetPage();
              }}
            />
          )}
        </div>
      </div>

      <div style={{ flex: 1, overflowY: 'auto', padding: '8px 24px' }}>
        {isLoading ? (
          <div style={{ padding: 48, textAlign: 'center' }}>
            <Spin />
          </div>
        ) : !data || data.records.length === 0 ? (
          <Empty
            description={isAll ? '暂无工单' : '你还没有提交过工单'}
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            style={{ padding: '48px 0' }}
          />
        ) : (
          <Table<TicketSummary>
            size="small"
            rowKey="id"
            columns={columns}
            dataSource={data.records}
            onRow={(r) => ({
              onClick: () => {
                if (shouldSkipRowNav()) return;
                onSelect(r.id);
              },
              style: {
                cursor: 'pointer',
                background: r.id === selectedId ? 'var(--brand-bg)' : undefined,
              },
            })}
            pagination={{
              current: page,
              pageSize: PAGE_SIZE,
              total: data.total,
              onChange: setPage,
              showSizeChanger: false,
              size: 'small',
            }}
          />
        )}
      </div>
    </div>
  );
}
