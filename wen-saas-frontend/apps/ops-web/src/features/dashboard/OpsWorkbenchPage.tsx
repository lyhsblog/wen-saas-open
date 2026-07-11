import { useState } from 'react';
import { Button, Empty, Skeleton } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { ReloadOutlined, PlusOutlined, RightOutlined } from '@ant-design/icons';
import { useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router';
import type {
  CreatedTenantView,
  OpsDashboardOverview,
  OpsRecentActivity,
  OpsTodoItem,
  OpsTodoSeverity,
} from '@wen-saas/api-client';
import { CountChip, DataTable, formatTodayOrDate } from '@wen-saas/shared-ui';
import { OpenTenantWizardModal } from '../tenants/OpenTenantWizardModal';
import { MfaNudgeBanner } from './MfaNudgeBanner';
import { dashboardKeys, useOverview, useRecentActivities, useTodos } from './dashboardQueries';

const MONO: React.CSSProperties = { fontFamily: 'var(--font-mono)' };

/** 严重度 → 色 + 名称：urgent 渲染「急」字胶囊，warn/info 渲染纯圆点（label 供 aria-label/title，色弱可辨）。 */
const SEVERITY_META: Record<OpsTodoSeverity, { color: string; label: string }> = {
  urgent: { color: 'var(--danger)', label: '紧急' },
  warn: { color: 'var(--warning)', label: '需关注' },
  info: { color: 'var(--info, var(--brand))', label: '提示' },
};

function todayLabel(): string {
  const now = new Date();
  const wd = ['日', '一', '二', '三', '四', '五', '六'][now.getDay()];
  return `${now.toLocaleDateString('zh-CN')} 周${wd}`;
}

type KpiSpec = {
  label: string;
  value: number;
  delta: string;
  tone: 'up' | 'down' | 'danger' | 'muted';
};

function kpiSpecs(o: OpsDashboardOverview): KpiSpec[] {
  // 后端 Long 差值按本仓约定序列化为字符串，转 Number 后严格比较才准（否则 "0"===0 为 false → 误判涨跌）。
  const d = Number(o.activeTenantsDeltaVsLastMonth);
  return [
    {
      label: '在租租户数',
      value: o.activeTenants,
      delta: d === 0 ? '与上月持平' : `${d > 0 ? '+' : ''}${d} 同比上月`,
      tone: d === 0 ? 'muted' : d > 0 ? 'up' : 'down',
    },
    { label: '本月新增租户', value: o.newTenantsThisMonth, delta: '本月新开通', tone: 'up' },
    { label: '本月续费数', value: o.renewalsThisMonth, delta: '续费操作次数', tone: 'muted' },
    {
      label: '30 天内到期',
      value: o.expiringIn30Days,
      delta: `其中 ${o.expiringIn7Days} 个 7 日内`,
      tone: o.expiringIn7Days > 0 ? 'danger' : 'muted',
    },
  ];
}

function toneColor(tone: KpiSpec['tone']): string {
  switch (tone) {
    case 'up':
      return 'var(--success)';
    case 'down':
    case 'danger':
      return 'var(--danger)';
    default:
      return 'var(--text-tertiary)';
  }
}

export function OpsWorkbenchPage() {
  const qc = useQueryClient();
  const navigate = useNavigate();
  const [wizardOpen, setWizardOpen] = useState(false);

  const overview = useOverview();
  const todos = useTodos();
  const activities = useRecentActivities(20);

  const refreshing = overview.isFetching || todos.isFetching || activities.isFetching;
  const refresh = () => {
    void qc.invalidateQueries({ queryKey: dashboardKeys.all });
  };

  const handleCreated = (view: CreatedTenantView) => {
    setWizardOpen(false);
    void navigate(`/tenants?selected=${encodeURIComponent(view.tenantId)}&tab=basic`);
  };

  const goTodo = (t: OpsTodoItem) => {
    if (t.actionUrl) {
      void navigate(t.actionUrl);
    } else if (t.targetTenantId) {
      void navigate(`/tenants?selected=${encodeURIComponent(t.targetTenantId)}&tab=basic`);
    }
  };

  const activityCols: ColumnsType<OpsRecentActivity> = [
    {
      title: '租户',
      dataIndex: 'targetTenantName',
      key: 'tenant',
      render: (v: string | null) => <b>{v ?? '—'}</b>,
    },
    {
      title: '事件',
      key: 'event',
      render: (_: unknown, a) => (
        <>
          {a.action}
          {a.objectDesc ? (
            <span style={{ color: 'var(--text-tertiary)', marginLeft: 6 }}>{a.objectDesc}</span>
          ) : null}
        </>
      ),
    },
    {
      title: '操作人',
      key: 'operator',
      render: (_: unknown, a) => a.operatorName ?? <span style={MONO}>#{a.operatorId}</span>,
    },
    {
      title: '时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (v: string | null) => (
        <span style={{ color: 'var(--text-tertiary)' }}>{formatTodayOrDate(v)}</span>
      ),
    },
    {
      title: '操作',
      key: 'actions',
      width: 80,
      align: 'right',
      render: (_: unknown, a) =>
        a.targetTenantId ? (
          <Button
            type="link"
            size="small"
            onClick={() =>
              void navigate(
                `/tenants?selected=${encodeURIComponent(a.targetTenantId as string)}&tab=basic`,
              )
            }
          >
            详情
          </Button>
        ) : (
          <span style={{ color: 'var(--text-tertiary)' }}>—</span>
        ),
    },
  ];

  return (
    <div
      style={{
        padding: 20,
        display: 'flex',
        flexDirection: 'column',
        gap: 16,
        background: 'var(--bg)',
        height: '100%',
        overflowY: 'auto',
      }}
    >
      <MfaNudgeBanner />

      {/* 页头 */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
        <div style={{ fontSize: 18, fontWeight: 600 }}>运营工作台</div>
        <span style={{ fontSize: 12, color: 'var(--text-tertiary)' }}>{todayLabel()}</span>
        <div style={{ flex: 1 }} />
        <Button size="small" icon={<ReloadOutlined />} loading={refreshing} onClick={refresh}>
          刷新
        </Button>
        <Button
          size="small"
          type="primary"
          icon={<PlusOutlined />}
          onClick={() => setWizardOpen(true)}
        >
          开通租户
        </Button>
      </div>

      {/* KPI 4 卡 */}
      {overview.isLoading || !overview.data ? (
        <Skeleton active paragraph={{ rows: 2 }} />
      ) : (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 12 }}>
          {kpiSpecs(overview.data).map((m) => (
            <div
              key={m.label}
              style={{
                background: 'var(--bg-elev)',
                border: '1px solid var(--border)',
                borderRadius: 'var(--r-md, 8px)',
                padding: 14,
              }}
            >
              <div style={{ fontSize: 13, color: 'var(--text-secondary)' }}>{m.label}</div>
              <div
                style={{
                  fontSize: 22,
                  fontWeight: 600,
                  marginTop: 4,
                  fontVariantNumeric: 'tabular-nums',
                }}
              >
                {m.value}
              </div>
              <div style={{ fontSize: 11, color: toneColor(m.tone), marginTop: 4 }}>{m.delta}</div>
            </div>
          ))}
        </div>
      )}

      {/* 今日待办 */}
      <section
        style={{
          background: 'var(--bg-elev)',
          border: '1px solid var(--border)',
          borderRadius: 'var(--r-md, 8px)',
          overflow: 'hidden',
        }}
      >
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 8,
            padding: '12px 16px',
            borderBottom: '1px solid var(--divider)',
            fontWeight: 600,
            fontSize: 14,
          }}
        >
          今日待办
          {todos.data && todos.data.length > 0 && <CountChip>{todos.data.length}</CountChip>}
        </div>
        <div style={{ padding: '4px 16px', fontSize: 13 }}>
          {todos.isLoading ? (
            <Skeleton active paragraph={{ rows: 3 }} style={{ padding: '12px 0' }} />
          ) : !todos.data || todos.data.length === 0 ? (
            <Empty
              image={Empty.PRESENTED_IMAGE_SIMPLE}
              description="暂无待办"
              style={{ margin: '16px 0' }}
            />
          ) : (
            todos.data.map((t, i) => {
              const meta = SEVERITY_META[t.severity];
              return (
                <div
                  key={`${t.type}-${t.targetTenantId ?? i}`}
                  onClick={() => goTodo(t)}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 10,
                    padding: '8px 0',
                    borderBottom: i < todos.data.length - 1 ? '1px dashed var(--divider)' : 'none',
                    cursor: t.actionUrl || t.targetTenantId ? 'pointer' : 'default',
                  }}
                >
                  {t.severity === 'urgent' ? (
                    <span
                      title={meta.label}
                      style={{
                        flexShrink: 0,
                        minWidth: 18,
                        height: 18,
                        lineHeight: '18px',
                        textAlign: 'center',
                        fontSize: 11,
                        color: '#fff',
                        background: meta.color,
                        borderRadius: 9,
                        padding: '0 5px',
                      }}
                    >
                      急
                    </span>
                  ) : (
                    <span
                      role="img"
                      aria-label={meta.label}
                      title={meta.label}
                      style={{
                        flexShrink: 0,
                        width: 8,
                        height: 8,
                        borderRadius: '50%',
                        background: meta.color,
                      }}
                    />
                  )}
                  <span style={{ flex: 1 }}>
                    {t.title}
                    {t.summary ? (
                      <span style={{ color: 'var(--text-tertiary)', marginLeft: 8 }}>
                        {t.summary}
                      </span>
                    ) : null}
                  </span>
                  {t.actionUrl || t.targetTenantId ? (
                    <RightOutlined style={{ fontSize: 12, color: 'var(--text-tertiary)' }} />
                  ) : null}
                </div>
              );
            })
          )}
        </div>
      </section>

      {/* 最新租户动态 */}
      <section
        style={{
          background: 'var(--bg-elev)',
          border: '1px solid var(--border)',
          borderRadius: 'var(--r-md, 8px)',
          overflow: 'hidden',
        }}
      >
        <div
          style={{
            padding: '12px 16px',
            borderBottom: '1px solid var(--divider)',
            fontWeight: 600,
            fontSize: 14,
          }}
        >
          最新租户动态
        </div>
        <DataTable<OpsRecentActivity>
          columns={activityCols}
          dataSource={activities.data}
          loading={activities.isLoading}
          pagination={false}
          empty={{ description: '暂无动态' }}
        />
      </section>

      <OpenTenantWizardModal
        open={wizardOpen}
        onClose={() => setWizardOpen(false)}
        onCreated={handleCreated}
      />
    </div>
  );
}
