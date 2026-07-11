import { useState } from 'react';
import {
  CopyableText,
  CountChip,
  DataTable,
  ErrorState,
  formatDate,
  shouldSkipRowNav,
  StatusPill,
  useListQuery,
} from '@wen-saas/shared-ui';
import { App, Button, Empty, Input, Select, Spin } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { ExportOutlined, PlusOutlined, TeamOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { opsTenantApi } from '@wen-saas/api-client';
import type {
  CreatedTenantView,
  ListTenantsQuery,
  TenantListItemView,
  TenantSort,
} from '@wen-saas/api-client';
import { http } from '../../lib/http';
import { statusBadgeOf } from './utils/statusOf';
import { OpenTenantWizardModal } from './OpenTenantWizardModal';
import { useOpsPackages } from './hooks/useOpsPackages';

const SORT_OPTIONS: { label: string; value: TenantSort }[] = [
  { label: '最近活跃', value: 'lastActiveAt:desc' },
  { label: '创建时间', value: 'createdAt:desc' },
  { label: '最近到期', value: 'nearestExpireAt:asc' },
];

/** 套餐 code → 展示名（目录未命中回退原 code；planCodes 缺失时回退数量原值）。 */
function planLabelOf(t: TenantListItemView, byCode: Map<string, { name: string }>): string {
  if (t.planCodes.length > 0) {
    return t.planCodes.map((c) => byCode.get(c)?.name ?? c).join('、');
  }
  return t.planCount > 0 ? `套餐 ${t.planCount}` : '';
}

/** 最近到期日色阶（OPS-T-021）：≤7 天红、≤30 天黄、其余 tertiary。 */
function expireColor(iso: string | null): string {
  if (!iso) return 'var(--text-tertiary)';
  const days = Math.ceil((new Date(iso).getTime() - Date.now()) / (1000 * 60 * 60 * 24));
  if (days <= 7) return 'var(--danger)';
  if (days <= 30) return 'var(--warning)';
  return 'var(--text-tertiary)';
}

type Props = {
  selectedId: string | null;
  onSelect: (id: string) => void;
  compact: boolean;
};

const PAGE_SIZE = 20;
const MONO: React.CSSProperties = { fontFamily: 'var(--font-mono)' };

export function OpsTenantListPane({ selectedId, onSelect, compact }: Props) {
  const { message } = App.useApp();
  const [wizardOpen, setWizardOpen] = useState(false);
  const [exporting, setExporting] = useState(false);

  const { filters, page, setPage, setFilter } = useListQuery<{
    keyword: string;
    status?: 0 | 1 | 2;
    expiringWithinDays?: number;
    hasSeatAddon?: boolean;
    packageCode: string[];
    sort: TenantSort;
  }>({ keyword: '', packageCode: [], sort: 'lastActiveAt:desc' }, { size: PAGE_SIZE });

  const { catalog, byCode } = useOpsPackages();

  const query: ListTenantsQuery = {
    page,
    pageSize: PAGE_SIZE,
    sort: filters.sort,
    ...(filters.keyword ? { keyword: filters.keyword } : {}),
    ...(filters.status != null ? { status: filters.status } : {}),
    ...(filters.expiringWithinDays != null
      ? { expiringWithinDays: filters.expiringWithinDays }
      : {}),
    ...(filters.hasSeatAddon != null ? { hasSeatAddon: filters.hasSeatAddon } : {}),
    ...(filters.packageCode.length > 0 ? { packageCode: filters.packageCode } : {}),
  };

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['ops', 'tenants', query],
    queryFn: () => opsTenantApi(http).listTenants(query),
  });

  const handleExport = async () => {
    setExporting(true);
    try {
      const blob = await opsTenantApi(http).exportTenants(query);
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `tenants-${new Date().toISOString().slice(0, 10).replace(/-/g, '')}.csv`;
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);
    } catch {
      void message.error('导出失败，请稍后重试');
    } finally {
      setExporting(false);
    }
  };

  // 区分「无数据」与「无匹配结果」空态（O6-1）：任一搜索/筛选生效时按「无匹配」提示。
  const hasFilter =
    !!filters.keyword ||
    filters.status != null ||
    filters.expiringWithinDays != null ||
    filters.hasSeatAddon != null ||
    filters.packageCode.length > 0;

  const handleCreated = (view: CreatedTenantView) => {
    setWizardOpen(false);
    onSelect(view.tenantId);
  };

  // ─────────────── 紧凑卡片视图（详情打开后的左侧 380px） ───────────────
  if (compact) {
    return (
      <div
        style={{
          width: 380,
          flexShrink: 0,
          display: 'flex',
          flexDirection: 'column',
          background: 'var(--bg-elev)',
          borderRight: '1px solid var(--border)',
          overflow: 'hidden',
        }}
      >
        <div
          style={{
            padding: '14px 16px',
            borderBottom: '1px solid var(--divider)',
            display: 'flex',
            flexDirection: 'column',
            gap: 10,
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <TeamOutlined style={{ color: 'var(--text-tertiary)' }} />
            <span style={{ fontWeight: 600, fontSize: 15 }}>租户列表</span>
            {data && (
              <CountChip>
                {data.items.length} / {data.total}
              </CountChip>
            )}
            <div style={{ flex: 1 }} />
            <Button
              type="primary"
              size="small"
              icon={<PlusOutlined />}
              onClick={() => setWizardOpen(true)}
            >
              开通
            </Button>
          </div>
          <Input.Search
            size="small"
            placeholder="搜索租户名 / 标识 / 主管理员"
            allowClear
            onSearch={(v) => setFilter('keyword', v)}
          />
        </div>

        <div style={{ flex: 1, overflowY: 'auto' }}>
          {isLoading ? (
            <div style={{ padding: 48, textAlign: 'center' }}>
              <Spin />
            </div>
          ) : isError ? (
            <ErrorState
              title="加载列表失败"
              primaryAction={{ label: '重试', onClick: () => void refetch() }}
            />
          ) : !data?.items.length ? (
            <Empty
              description={
                hasFilter
                  ? '未找到匹配的租户，试试调整搜索或筛选条件'
                  : '暂无租户，点击右上角开通第一个租户'
              }
              style={{ padding: '48px 0' }}
              image={Empty.PRESENTED_IMAGE_SIMPLE}
            />
          ) : (
            data.items.map((t) => {
              const badge = statusBadgeOf(t);
              const isSelected = t.id === selectedId;
              return (
                <div
                  key={t.id}
                  onClick={() => {
                    if (shouldSkipRowNav()) return;
                    onSelect(t.id);
                  }}
                  style={{
                    padding: '12px 16px',
                    cursor: 'pointer',
                    background: isSelected ? 'var(--brand-bg)' : 'transparent',
                    borderLeft: `3px solid ${isSelected ? 'var(--brand)' : 'transparent'}`,
                    borderBottom: '1px solid var(--divider)',
                    transition: 'background .15s',
                  }}
                  onMouseEnter={(e) => {
                    if (!isSelected) e.currentTarget.style.background = 'var(--bg-muted)';
                  }}
                  onMouseLeave={(e) => {
                    if (!isSelected) e.currentTarget.style.background = 'transparent';
                  }}
                >
                  {/* 第 1 行：名字 + 状态徽章 */}
                  <div
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: 8,
                      marginBottom: 4,
                    }}
                  >
                    <span
                      style={{
                        fontWeight: 600,
                        fontSize: 14,
                        flex: 1,
                        minWidth: 0,
                        whiteSpace: 'nowrap',
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                      }}
                    >
                      {t.name}
                    </span>
                    <StatusPill badge={badge} />
                  </div>
                  {/* 第 2 行：编号 + 套餐数 + 加购席位（chip 排版） */}
                  <div
                    style={{
                      fontSize: 12,
                      color: 'var(--text-tertiary)',
                      display: 'flex',
                      gap: 6,
                      flexWrap: 'wrap',
                      alignItems: 'center',
                      marginBottom: 4,
                    }}
                  >
                    {t.tenantNo && <span style={MONO}>{t.tenantNo}</span>}
                    <CountChip>{planLabelOf(t, byCode) || '无套餐'}</CountChip>
                    {t.seatAddonTotal > 0 && <CountChip>+{t.seatAddonTotal} 席位</CountChip>}
                  </div>
                  {/* 第 3 行：主管理员 + 客户经理 */}
                  <div style={{ fontSize: 11, color: 'var(--text-tertiary)' }}>
                    主管理员：{t.adminUsername ?? '—'}
                    {t.csmName && (
                      <>
                        <span style={{ margin: '0 6px' }}>·</span>
                        <span>客户经理：{t.csmName}</span>
                      </>
                    )}
                  </div>
                </div>
              );
            })
          )}
        </div>

        <OpenTenantWizardModal
          open={wizardOpen}
          onClose={() => setWizardOpen(false)}
          onCreated={handleCreated}
        />
      </div>
    );
  }

  // ─────────────── 完整表格视图 ───────────────
  const columns: ColumnsType<TenantListItemView> = [
    {
      title: '租户名',
      dataIndex: 'name',
      key: 'name',
      render: (v: string) => <span style={{ fontWeight: 600 }}>{v}</span>,
    },
    {
      title: '子域名',
      dataIndex: 'code',
      key: 'code',
      render: (v: string) => (
        <CopyableText value={v}>
          <code style={{ ...MONO, fontSize: 12, color: 'var(--text-tertiary)' }}>{v}</code>
        </CopyableText>
      ),
    },
    {
      title: '内部编号',
      dataIndex: 'tenantNo',
      key: 'tenantNo',
      render: (v: string) => (
        <CopyableText value={v}>
          <code style={{ ...MONO, fontSize: 12, color: 'var(--text-tertiary)' }}>{v}</code>
        </CopyableText>
      ),
    },
    {
      title: '状态',
      key: 'status',
      render: (_: unknown, t) => <StatusPill badge={statusBadgeOf(t)} />,
    },
    {
      title: '套餐',
      key: 'plan',
      render: (_: unknown, t) => (
        <>
          {planLabelOf(t, byCode) ? (
            <span>{planLabelOf(t, byCode)}</span>
          ) : (
            <span style={{ color: 'var(--text-tertiary)' }}>—</span>
          )}
          {t.seatAddonTotal > 0 && (
            <span style={{ marginLeft: 6 }}>
              <CountChip>+{t.seatAddonTotal} 席</CountChip>
            </span>
          )}
        </>
      ),
    },
    {
      title: '最近到期',
      dataIndex: 'nearestExpireAt',
      key: 'nearestExpireAt',
      render: (v: string | null) => <span style={{ color: expireColor(v) }}>{formatDate(v)}</span>,
    },
    {
      title: '客户经理',
      dataIndex: 'csmName',
      key: 'csmName',
      render: (v: string | null) => v ?? <span style={{ color: 'var(--text-tertiary)' }}>—</span>,
    },
    {
      title: '主管理员',
      dataIndex: 'adminUsername',
      key: 'adminUsername',
      render: (v: string | null) => v ?? '—',
    },
    {
      title: '最后活跃',
      dataIndex: 'lastActiveAt',
      key: 'lastActiveAt',
      render: (v: string | null) => (
        <span style={{ color: 'var(--text-tertiary)' }}>{formatDate(v)}</span>
      ),
    },
    {
      title: '开通时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (v: string | null) => (
        <span style={{ color: 'var(--text-tertiary)' }}>{formatDate(v)}</span>
      ),
    },
    {
      title: '操作',
      key: 'actions',
      width: 100,
      align: 'right',
      render: (_: unknown, t) => (
        <Button
          type="link"
          size="small"
          onClick={(e) => {
            e.stopPropagation();
            onSelect(t.id);
          }}
        >
          详情
        </Button>
      ),
    },
  ];

  return (
    <div
      style={{
        flex: 1,
        display: 'flex',
        flexDirection: 'column',
        background: 'var(--bg-elev)',
        overflow: 'hidden',
      }}
    >
      {/* 工具栏 */}
      <div
        style={{
          padding: '14px 24px 0',
          display: 'flex',
          alignItems: 'center',
          gap: 8,
        }}
      >
        <span style={{ fontWeight: 600, fontSize: 15 }}>租户列表</span>
        {data && (
          <CountChip>
            {data.items.length} / {data.total}
          </CountChip>
        )}
        <div style={{ flex: 1 }} />
        <Button
          icon={<ExportOutlined />}
          size="small"
          loading={exporting}
          onClick={() => void handleExport()}
        >
          导出
        </Button>
        <Button
          type="primary"
          size="small"
          icon={<PlusOutlined />}
          onClick={() => setWizardOpen(true)}
        >
          开通租户
        </Button>
      </div>

      {/* 筛选条 */}
      <div
        style={{
          padding: '12px 24px',
          display: 'flex',
          gap: 8,
          flexWrap: 'wrap',
          borderBottom: '1px solid var(--divider)',
        }}
      >
        <Input.Search
          placeholder="搜索租户名 / 标识 / 主管理员"
          allowClear
          style={{ width: 280 }}
          onSearch={(v) => setFilter('keyword', v)}
        />
        <Select<0 | 1 | 2>
          style={{ width: 120 }}
          placeholder="全部状态"
          allowClear
          value={filters.status ?? null}
          onChange={(v) => setFilter('status', v ?? undefined)}
          options={[
            { label: '正常', value: 1 as const },
            { label: '已暂停', value: 0 as const },
            { label: '已过期', value: 2 as const },
          ]}
        />
        <Select<number>
          style={{ width: 140 }}
          placeholder="全部到期范围"
          allowClear
          value={filters.expiringWithinDays ?? null}
          onChange={(v) => setFilter('expiringWithinDays', v ?? undefined)}
          options={[
            { label: '7 天内到期', value: 7 },
            { label: '30 天内到期', value: 30 },
          ]}
        />
        <Select<string>
          style={{ width: 120 }}
          placeholder="全部加购"
          allowClear
          value={filters.hasSeatAddon == null ? null : filters.hasSeatAddon ? 'yes' : 'no'}
          onChange={(v) => setFilter('hasSeatAddon', v == null ? undefined : v === 'yes')}
          options={[
            { label: '已加购', value: 'yes' },
            { label: '未加购', value: 'no' },
          ]}
        />
        <Select<string[]>
          mode="multiple"
          style={{ minWidth: 180 }}
          placeholder="全部套餐"
          allowClear
          maxTagCount="responsive"
          value={filters.packageCode}
          onChange={(v) => setFilter('packageCode', v ?? [])}
          options={catalog.map((p) => ({ label: p.name, value: p.code }))}
        />
        <div style={{ flex: 1 }} />
        <Select<TenantSort>
          style={{ width: 140 }}
          value={filters.sort}
          onChange={(v) => setFilter('sort', v)}
          options={SORT_OPTIONS}
        />
      </div>

      {/* 表格 + 极简分页底栏（DataTable 承载 hover / 行选中高亮 / 空态 / 分页） */}
      <div style={{ flex: 1, minHeight: 0, overflow: 'hidden' }}>
        {isError ? (
          <ErrorState
            title="加载列表失败"
            primaryAction={{ label: '重试', onClick: () => void refetch() }}
          />
        ) : (
          <DataTable<TenantListItemView>
            columns={columns}
            dataSource={data?.items}
            loading={isLoading}
            {...(selectedId != null ? { selectedRowKey: selectedId } : {})}
            onRowClick={(t) => onSelect(t.id)}
            simplePagination={{
              // 后端 Long 可能序列化为字符串，数值化保证分页计算与判空可靠
              total: Number(data?.total ?? 0),
              page,
              pageSize: PAGE_SIZE,
              onChange: setPage,
            }}
            empty={{
              description: hasFilter
                ? '未找到匹配的租户，试试调整搜索或筛选条件'
                : '暂无租户，点击右上角开通第一个租户',
            }}
          />
        )}
      </div>

      <OpenTenantWizardModal
        open={wizardOpen}
        onClose={() => setWizardOpen(false)}
        onCreated={handleCreated}
      />
    </div>
  );
}
