import {
  CountChip,
  ErrorState,
  shouldSkipRowNav,
  StatusPill,
  TabChip,
  useListQuery,
} from '@wen-saas/shared-ui';
import { Empty, Input, Spin, Table } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { HeartOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { opsHealthApi } from '@wen-saas/api-client';
import type { HealthLevel, HealthListItem } from '@wen-saas/api-client';
import { http } from '../../lib/http';
import { healthLevelBadge, healthLevelColor, HEALTH_FILTER_TABS } from './utils/healthMeta';

type Props = {
  selectedId: string | null;
  onSelect: (id: string) => void;
  compact: boolean;
};

const PAGE_SIZE = 20;

export function OpsHealthListPane({ selectedId, onSelect, compact }: Props) {
  const { filters, page, setPage, setFilter } = useListQuery<{
    tab: HealthLevel | 'all';
    keyword: string;
  }>({ tab: 'all', keyword: '' }, { size: PAGE_SIZE });

  // tab='all' 为不过滤哨兵值、且 tab→level 改名，query 手工拼装。
  const query = {
    ...(filters.tab !== 'all' ? { level: filters.tab } : {}),
    ...(filters.keyword ? { keyword: filters.keyword } : {}),
    page,
    size: PAGE_SIZE,
  };

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['ops', 'health', 'list', query],
    queryFn: () => opsHealthApi(http).listTenants(query),
  });

  const columns: ColumnsType<HealthListItem> = [
    {
      title: '租户',
      dataIndex: 'tenantName',
      key: 'tenant',
      render: (_: unknown, r) => (
        <div style={{ minWidth: 0 }}>
          <div style={{ fontWeight: 600, fontSize: 14 }}>{r.tenantName ?? '—'}</div>
          {r.tenantNo && (
            <div style={{ fontSize: 12, color: 'var(--text-tertiary)' }}>{r.tenantNo}</div>
          )}
        </div>
      ),
    },
    {
      title: '评分',
      dataIndex: 'score',
      key: 'score',
      width: 120,
      render: (_: unknown, r) => (
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <span
            style={{
              fontSize: 18,
              fontWeight: 700,
              color: healthLevelColor(r.level),
              fontVariantNumeric: 'tabular-nums',
            }}
          >
            {r.score}
          </span>
          <StatusPill badge={healthLevelBadge(r.level)} />
        </div>
      ),
    },
    {
      title: '风险信号',
      dataIndex: 'riskSignals',
      key: 'signals',
      render: (_: unknown, r) =>
        r.riskSignals.length === 0 ? (
          <span style={{ color: 'var(--text-tertiary)', fontSize: 12 }}>无</span>
        ) : (
          <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap' }}>
            {r.riskSignals.map((s) => (
              <CountChip key={s.code}>{s.label}</CountChip>
            ))}
          </div>
        ),
    },
  ];

  const filterBar = (
    <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'center' }}>
      <div role="tablist" style={{ display: 'flex', gap: 4 }}>
        {HEALTH_FILTER_TABS.map((t) => (
          <TabChip
            key={t.key}
            active={filters.tab === t.key}
            onClick={() => setFilter('tab', t.key)}
          >
            {t.label}
          </TabChip>
        ))}
      </div>
      <div style={{ flex: 1 }} />
      <Input.Search
        size={compact ? 'small' : 'middle'}
        placeholder="搜索租户名 / 编号"
        allowClear
        style={compact ? undefined : { width: 240 }}
        onSearch={(v) => setFilter('keyword', v)}
      />
    </div>
  );

  return (
    <div
      style={{
        width: compact ? 540 : undefined,
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
          <HeartOutlined style={{ color: 'var(--text-tertiary)' }} />
          <span style={{ fontWeight: 600, fontSize: 15 }}>租户健康度</span>
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
            description="暂无健康度数据（评分每天凌晨计算）"
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            style={{ padding: '48px 0' }}
          />
        ) : (
          <Table<HealthListItem>
            size="small"
            rowKey="tenantId"
            columns={columns}
            dataSource={data.records}
            onRow={(r) => ({
              onClick: () => {
                if (shouldSkipRowNav()) return;
                onSelect(r.tenantId);
              },
              style: {
                cursor: 'pointer',
                background: r.tenantId === selectedId ? 'var(--brand-bg)' : undefined,
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
