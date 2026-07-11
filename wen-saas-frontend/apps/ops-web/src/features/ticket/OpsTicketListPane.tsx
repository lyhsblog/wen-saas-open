import {
  CopyableText,
  CountChip,
  ErrorState,
  shouldSkipRowNav,
  StatusPill,
  TabChip,
  useListQuery,
} from '@wen-saas/shared-ui';
import { Empty, Input, Select, Spin, Switch, Table } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { MessageOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { opsTicketApi } from '@wen-saas/api-client';
import type {
  TicketPriority,
  TicketSlaState,
  TicketStatus,
  TicketSummary,
  TicketType,
} from '@wen-saas/api-client';
import { http } from '../../lib/http';
import {
  fromNow,
  priorityBadge,
  PRIORITY_META,
  slaBadge,
  statusBadge,
  STATUS_TABS,
  TYPE_LABEL,
} from './utils/ticketMeta';

type Props = {
  selectedId: string | null;
  onSelect: (id: string) => void;
  compact: boolean;
};

const PAGE_SIZE = 20;

// 文案单源自 ticketMeta（→ @wen-saas/api-client）；此处仅定列表筛选的展示顺序。
const TYPE_OPTIONS: { value: TicketType; label: string }[] = (
  Object.keys(TYPE_LABEL) as TicketType[]
).map((value) => ({ value, label: TYPE_LABEL[value] }));

const PRIORITY_OPTIONS: { value: TicketPriority; label: string }[] = (
  ['URGENT', 'HIGH', 'NORMAL', 'LOW'] as TicketPriority[]
).map((value) => ({ value, label: PRIORITY_META[value].label }));

const SLA_OPTIONS: { value: TicketSlaState; label: string }[] = [
  { value: 'breach', label: 'SLA 超时' },
  { value: 'warning', label: 'SLA 临近' },
  { value: 'normal', label: 'SLA 正常' },
];

export function OpsTicketListPane({ selectedId, onSelect, compact }: Props) {
  const { filters, page, setPage, setFilter } = useListQuery<{
    tab: TicketStatus | 'all';
    type?: TicketType;
    priority?: TicketPriority;
    slaState?: TicketSlaState;
    mineOnly: boolean;
    keyword: string;
  }>({ tab: 'all', mineOnly: false, keyword: '' }, { size: PAGE_SIZE });

  // tab='all' 是「不按状态过滤」的哨兵值、且字段名需 tab→status，故 query 仍按语义手工拼装。
  const query = {
    ...(filters.tab !== 'all' ? { status: filters.tab } : {}),
    ...(filters.type ? { type: filters.type } : {}),
    ...(filters.priority ? { priority: filters.priority } : {}),
    ...(filters.slaState ? { slaState: filters.slaState } : {}),
    ...(filters.mineOnly ? { mineOnly: true } : {}),
    ...(filters.keyword ? { keyword: filters.keyword } : {}),
    page,
    size: PAGE_SIZE,
  };

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['ops', 'tickets', 'list', query],
    queryFn: () => opsTicketApi(http).list(query),
  });

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
            {r.tenantName && <span> · {r.tenantName}</span>}
            <span> · {TYPE_LABEL[r.type]}</span>
          </div>
        </div>
      ),
    },
    {
      title: '优先级',
      dataIndex: 'priority',
      key: 'priority',
      width: 88,
      render: (_: unknown, r) => <StatusPill badge={priorityBadge(r.priority)} />,
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 150,
      render: (_: unknown, r) => (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 4, alignItems: 'flex-start' }}>
          <StatusPill badge={statusBadge(r.status)} />
          {(r.status === 'PENDING' || r.status === 'PROCESSING') && (
            <StatusPill badge={slaBadge(r.slaState)} />
          )}
        </div>
      ),
    },
    {
      title: '受理人',
      dataIndex: 'assigneeName',
      key: 'assignee',
      width: 110,
      render: (_: unknown, r) =>
        r.assigneeName ? (
          <span style={{ fontSize: 13 }}>{r.assigneeName}</span>
        ) : (
          <span style={{ color: 'var(--text-tertiary)', fontSize: 12 }}>未认领</span>
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

  const filterBar = (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
      <div role="tablist" style={{ display: 'flex', gap: 4, flexWrap: 'wrap' }}>
        {STATUS_TABS.map((t) => (
          <TabChip
            key={t.key}
            active={filters.tab === t.key}
            onClick={() => setFilter('tab', t.key)}
          >
            {t.label}
          </TabChip>
        ))}
      </div>
      <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'center' }}>
        <Select
          size="small"
          allowClear
          placeholder="类型"
          style={{ width: 96 }}
          value={filters.type}
          options={TYPE_OPTIONS}
          onChange={(v: TicketType | undefined) => setFilter('type', v)}
        />
        <Select
          size="small"
          allowClear
          placeholder="优先级"
          style={{ width: 96 }}
          value={filters.priority}
          options={PRIORITY_OPTIONS}
          onChange={(v: TicketPriority | undefined) => setFilter('priority', v)}
        />
        <Select
          size="small"
          allowClear
          placeholder="SLA"
          style={{ width: 110 }}
          value={filters.slaState}
          options={SLA_OPTIONS}
          onChange={(v: TicketSlaState | undefined) => setFilter('slaState', v)}
        />
        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontSize: 13 }}>
          <Switch
            size="small"
            checked={filters.mineOnly}
            onChange={(v) => setFilter('mineOnly', v)}
          />
          仅我的
        </span>
        <div style={{ flex: 1 }} />
        <Input.Search
          size="small"
          placeholder="搜索标题 / 工单号"
          allowClear
          style={{ width: 200 }}
          onSearch={(v) => setFilter('keyword', v)}
        />
      </div>
    </div>
  );

  return (
    <div
      style={{
        width: compact ? 620 : undefined,
        flex: compact ? undefined : 1,
        flexShrink: compact ? 0 : undefined,
        display: 'flex',
        flexDirection: 'column',
        background: 'var(--bg-elev)',
        borderRight: compact ? '1px solid var(--border)' : undefined,
        overflow: 'hidden',
      }}
    >
      <div
        style={{
          padding: compact ? '14px 16px' : '14px 24px',
          borderBottom: '1px solid var(--divider)',
          display: 'flex',
          flexDirection: 'column',
          gap: 12,
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <MessageOutlined style={{ color: 'var(--text-tertiary)' }} />
          <span style={{ fontWeight: 600, fontSize: 15 }}>工单管理</span>
          {data && <CountChip>{data.total}</CountChip>}
        </div>
        {filterBar}
      </div>
      <div style={{ flex: 1, overflowY: 'auto', padding: compact ? '8px 12px' : '8px 24px' }}>
        {isLoading ? (
          <div style={{ padding: 48, textAlign: 'center' }}>
            <Spin />
          </div>
        ) : isError ? (
          <ErrorState
            title="加载列表失败"
            primaryAction={{ label: '重试', onClick: () => void refetch() }}
          />
        ) : !data || data.records.length === 0 ? (
          <Empty
            description="暂无工单"
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
