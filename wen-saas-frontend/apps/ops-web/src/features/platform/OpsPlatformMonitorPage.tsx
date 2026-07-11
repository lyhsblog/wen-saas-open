import { useEffect, useRef, useState, type ReactNode, type RefObject } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { Button, Card, Empty, Segmented, Skeleton, Spin, Table } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import { CountChip } from '@wen-saas/shared-ui';
import type {
  OpsMonitorModuleMetric,
  OpsMonitorRange,
  OpsMonitorTrendType,
} from '@wen-saas/api-client';
import { PlanDonut, SparkArea, TrendChart } from './charts';
import { CHART_COLORS, PLAN_PALETTE, formatBytes, type DonutItem } from './chartUtils';
import {
  platformKeys,
  useMonitorApiWebhook,
  useMonitorHealth,
  useMonitorModules,
  useMonitorOverview,
  useMonitorPlans,
  useMonitorRenewals,
  useMonitorResources,
  useMonitorTrends,
} from './platformQueries';

const RANGES: { value: OpsMonitorRange; label: string }[] = [
  { value: '7d', label: '最近 7 天' },
  { value: '30d', label: '最近 30 天' },
  { value: 'mtd', label: '本月至今' },
  { value: 'qtr', label: '本季度' },
];

const TREND_TABS: {
  value: OpsMonitorTrendType;
  label: string;
  c1: string;
  c2: string;
}[] = [
  { value: 'active', label: '活跃趋势', c1: CHART_COLORS.brand, c2: CHART_COLORS.tertiary },
  { value: 'tenant', label: '新增 / 流失', c1: CHART_COLORS.success, c2: CHART_COLORS.danger },
  { value: 'api', label: 'API 调用', c1: CHART_COLORS.purple, c2: CHART_COLORS.danger },
  { value: 'ticket', label: '工单趋势', c1: CHART_COLORS.warning, c2: CHART_COLORS.tertiary },
];

/**
 * 元素首次进入视口后永久返回 true（IntersectionObserver 一次性触发），
 * 用于首屏外区块的取数门控：进视口才发请求，避免 8 个 query 首屏并发。
 */
function useInViewOnce<T extends HTMLElement>(): [RefObject<T | null>, boolean] {
  const ref = useRef<T>(null);
  const [inView, setInView] = useState(false);
  useEffect(() => {
    const el = ref.current;
    if (inView || !el) {
      return;
    }
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((e) => e.isIntersecting)) {
          setInView(true);
          observer.disconnect();
        }
      },
      // 提前 200px 预取，滚到区块时数据大概率已就绪
      { rootMargin: '200px' },
    );
    observer.observe(el);
    return () => observer.disconnect();
  }, [inView]);
  return [ref, inView];
}

function SectionCard({
  title,
  extra,
  children,
}: {
  title: string;
  extra?: ReactNode;
  children: ReactNode;
}) {
  return (
    <Card size="small" title={title} extra={extra} styles={{ body: { padding: 16 } }}>
      {children}
    </Card>
  );
}

// 基数低于此值时，百分比环比无统计意义（如 DAU 仅个位数时的 ±xx% 波动），不染涨跌色。
const SMALL_SAMPLE = 10;

function KpiCard({
  label,
  value,
  delta,
  sub,
  spark,
  positive,
  neutral = false,
  smallSample = false,
}: {
  label: string;
  value: string;
  delta: string;
  sub: string;
  spark: number[];
  positive: boolean;
  /** 无涨跌语义的指标（持平、或如「在处工单」这类计数）走中性灰，不渲染涨跌色。 */
  neutral?: boolean;
  /** 基数过小时百分比波动无统计意义，显「样本过小」中性灰，避免小样本误报涨跌。 */
  smallSample?: boolean;
}) {
  const effDelta = smallSample ? '样本过小' : delta;
  const isNeutral = neutral || smallSample;
  const color = isNeutral
    ? 'var(--text-tertiary)'
    : positive
      ? CHART_COLORS.success
      : CHART_COLORS.danger;
  return (
    <div
      style={{
        border: '1px solid var(--border)',
        borderRadius: 8,
        padding: 16,
        display: 'flex',
        flexDirection: 'column',
        gap: 8,
        background: 'var(--bg-elev)',
      }}
    >
      <span style={{ fontSize: 13, color: 'var(--text-secondary)' }}>{label}</span>
      <div style={{ display: 'flex', alignItems: 'baseline', gap: 8 }}>
        <span style={{ fontSize: 28, fontWeight: 700, fontVariantNumeric: 'tabular-nums' }}>
          {value}
        </span>
        <span style={{ marginLeft: 'auto', fontSize: 12, fontWeight: 600, color }}>{effDelta}</span>
      </div>
      <SparkArea
        values={spark}
        color={isNeutral || !positive ? CHART_COLORS.tertiary : CHART_COLORS.brand}
        w={260}
        h={34}
      />
      <span style={{ fontSize: 12, color: 'var(--text-tertiary)' }}>{sub}</span>
    </div>
  );
}

export function OpsPlatformMonitorPage() {
  const qc = useQueryClient();
  const [range, setRange] = useState<OpsMonitorRange>('7d');
  const [trendTab, setTrendTab] = useState<OpsMonitorTrendType>('active');

  // 首屏只加载 KPI / 趋势 / 模块三个区块；下方两行网格进视口后再取数
  const [planRowRef, planRowInView] = useInViewOnce<HTMLDivElement>();
  const [resourceRowRef, resourceRowInView] = useInViewOnce<HTMLDivElement>();

  const overview = useMonitorOverview();
  const trends = useMonitorTrends(trendTab, range);
  const modules = useMonitorModules();
  const plans = useMonitorPlans({ enabled: planRowInView });
  const renewals = useMonitorRenewals({ enabled: planRowInView });
  const resources = useMonitorResources({ enabled: resourceRowInView });
  const apiWebhook = useMonitorApiWebhook({ enabled: resourceRowInView });
  const health = useMonitorHealth({ enabled: resourceRowInView });

  const refreshing =
    overview.isFetching ||
    modules.isFetching ||
    plans.isFetching ||
    renewals.isFetching ||
    resources.isFetching ||
    apiWebhook.isFetching ||
    health.isFetching;

  const refresh = () => {
    void qc.invalidateQueries({ queryKey: platformKeys.all });
  };

  const ov = overview.data;
  const trendMeta = TREND_TABS.find((t) => t.value === trendTab)!;

  const planItems: DonutItem[] = (plans.data ?? []).map((p, i) => ({
    name: p.packageName,
    count: p.count,
    color: PLAN_PALETTE[i % PLAN_PALETTE.length] ?? CHART_COLORS.brand,
  }));
  const planTotal = planItems.reduce((s, p) => s + p.count, 0) || 1;

  const maxCalls = Math.max(1, ...(modules.data ?? []).map((m) => m.calls));
  const totalStore = resources.data
    ? resources.data.platformSize + resources.data.tenantOssSize
    : 0;
  const platformPct = totalStore > 0 ? (resources.data!.platformSize / totalStore) * 100 : 0;

  const healthTotal = health.data
    ? health.data.healthy + health.data.warning + health.data.danger
    : 0;
  const circ = 2 * Math.PI * 38;

  return (
    <div
      style={{
        padding: 20,
        height: '100%',
        overflowY: 'auto',
        display: 'flex',
        flexDirection: 'column',
        gap: 16,
      }}
    >
      {/* Header */}
      <div style={{ display: 'flex', alignItems: 'flex-start', gap: 12, flexWrap: 'wrap' }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <span style={{ fontSize: 18, fontWeight: 600 }}>平台监控</span>
            <CountChip>业务层面</CountChip>
            <CountChip tone="brand">实时聚合</CountChip>
          </div>
          <span style={{ fontSize: 12, color: 'var(--text-tertiary)' }}>
            全局视角的业务运营指标；技术层面监控由架构阶段统一规划，暂未开放
          </span>
        </div>
        <div style={{ flex: 1 }} />
        <Button icon={<ReloadOutlined />} loading={refreshing} onClick={refresh}>
          刷新
        </Button>
      </div>

      {/* 4 KPI 卡 */}
      <Spin spinning={overview.isLoading}>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 12 }}>
          {ov ? (
            <>
              <KpiCard
                label="总租户数"
                value={String(ov.activeTenants)}
                delta={`本月新增 ${ov.activeTenantsDelta}`}
                sub={`近 14 天趋势`}
                spark={ov.tenantSpark}
                positive
              />
              <KpiCard
                label="DAU 活跃用户"
                value={String(ov.dau)}
                delta={`${ov.dauDeltaPct > 0 ? '+' : ''}${ov.dauDeltaPct}% vs 昨日`}
                sub="按登录去重"
                spark={ov.dauSpark}
                positive={ov.dauDeltaPct >= 0}
                neutral={ov.dauDeltaPct === 0}
                smallSample={ov.dau < SMALL_SAMPLE}
              />
              <KpiCard
                label="API 调用 (今日)"
                value={String(ov.apiCallsToday)}
                delta={`${ov.apiCallsDeltaPct > 0 ? '+' : ''}${ov.apiCallsDeltaPct}% vs 昨日`}
                sub="开放 API 调用"
                spark={ov.apiSpark}
                positive={ov.apiCallsDeltaPct >= 0}
                neutral={ov.apiCallsDeltaPct === 0}
                smallSample={ov.apiCallsToday < SMALL_SAMPLE}
              />
              <KpiCard
                label="在处工单"
                value={String(ov.openTickets)}
                delta={`近 7 天新建 ${ov.openTicketsDelta}`}
                sub={`30 天到期 ${ov.expiringIn30Days} · 7 天 ${ov.expiringIn7Days}`}
                spark={ov.ticketSpark}
                positive={false}
                neutral
              />
            </>
          ) : null}
        </div>
      </Spin>

      {/* 趋势图 */}
      <SectionCard
        title="趋势分析"
        extra={
          <Segmented size="small" value={range} onChange={(v) => setRange(v)} options={RANGES} />
        }
      >
        <div style={{ display: 'flex', gap: 8, marginBottom: 12, flexWrap: 'wrap' }}>
          <Segmented
            value={trendTab}
            onChange={(v) => setTrendTab(v)}
            options={TREND_TABS.map((t) => ({ value: t.value, label: t.label }))}
          />
          <div style={{ flex: 1 }} />
          {trends.data ? (
            <div style={{ display: 'flex', alignItems: 'center', gap: 16, fontSize: 12 }}>
              <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
                <span
                  style={{
                    width: 14,
                    height: 2,
                    background: trendMeta.c1,
                    display: 'inline-block',
                  }}
                />
                {trends.data.y1Label}
              </span>
              <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6, color: 'var(--text-tertiary)' }}>
                <span
                  style={{
                    width: 14,
                    borderTop: `1.5px dashed ${trendMeta.c2}`,
                    display: 'inline-block',
                  }}
                />
                {trends.data.y2Label}
              </span>
            </div>
          ) : null}
        </div>
        {trends.isLoading ? (
          <Skeleton active title={false} paragraph={{ rows: 5 }} />
        ) : trends.data ? (
          <TrendChart
            dates={trends.data.dates}
            y1={trends.data.y1}
            y2={trends.data.y2}
            c1={trendMeta.c1}
            c2={trendMeta.c2}
          />
        ) : (
          <Empty description="暂无趋势数据" image={Empty.PRESENTED_IMAGE_SIMPLE} />
        )}
      </SectionCard>

      {/* 业务模块使用情况 */}
      <SectionCard
        title="业务模块使用情况"
        extra={<span style={{ fontSize: 12, color: 'var(--text-tertiary)' }}>近 30 天 · 仅真实注册模块</span>}
      >
        <Table
          size="small"
          rowKey="code"
          loading={modules.isLoading}
          pagination={false}
          locale={{
            emptyText: (
              <Empty
                description="近 30 天暂无业务模块访问记录"
                image={Empty.PRESENTED_IMAGE_SIMPLE}
              />
            ),
          }}
          dataSource={modules.data ?? []}
          columns={[
            {
              title: '模块',
              dataIndex: 'name',
              render: (name: string, r) => (
                <span>
                  <b>{name}</b>{' '}
                  <span style={{ color: 'var(--text-disabled)', fontSize: 11, marginLeft: 6 }}>{r.code}</span>
                </span>
              ),
            },
            { title: '活跃租户', dataIndex: 'enabledTenants', align: 'right', width: 100 },
            {
              title: '调用次数',
              dataIndex: 'calls',
              align: 'right',
              width: 110,
              render: (v: number) => v.toLocaleString(),
            },
            { title: 'DAU', dataIndex: 'dau', align: 'right', width: 80 },
            {
              title: '调用占比',
              dataIndex: 'calls',
              key: 'pct',
              width: 200,
              render: (v: number) => {
                const pct = (v / maxCalls) * 100;
                return (
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                    <div
                      style={{
                        flex: 1,
                        height: 6,
                        background: 'var(--divider)',
                        borderRadius: 3,
                        overflow: 'hidden',
                      }}
                    >
                      <div
                        style={{ height: '100%', width: `${pct}%`, background: CHART_COLORS.brand }}
                      />
                    </div>
                    <span style={{ fontSize: 11, color: 'var(--text-tertiary)', width: 36, textAlign: 'right' }}>
                      {pct.toFixed(0)}%
                    </span>
                  </div>
                );
              },
            },
            {
              title: '环比增长',
              dataIndex: 'growthPct',
              align: 'right',
              width: 100,
              render: (g: number, m: OpsMonitorModuleMetric) =>
                m.dau < SMALL_SAMPLE ? (
                  <span style={{ color: 'var(--text-tertiary)', fontWeight: 500 }}>样本过小</span>
                ) : g === 0 ? (
                  <span style={{ color: 'var(--text-tertiary)', fontWeight: 500 }}>0%</span>
                ) : (
                  <span
                    style={{
                      color: g > 0 ? CHART_COLORS.success : CHART_COLORS.danger,
                      fontWeight: 500,
                    }}
                  >
                    {g > 0 ? '↑' : '↓'} {Math.abs(g)}%
                  </span>
                ),
            },
          ]}
        />
      </SectionCard>

      {/* 套餐分布 + 续费率（进视口再取数） */}
      <div ref={planRowRef} style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
        <SectionCard
          title="套餐包分布"
          extra={<span style={{ fontSize: 12, color: 'var(--text-tertiary)' }}>共 {planTotal} 个活跃订阅</span>}
        >
          {plans.isPending ? (
            <Skeleton active title={false} paragraph={{ rows: 4 }} />
          ) : planItems.length ? (
            <div style={{ display: 'flex', alignItems: 'center', gap: 24 }}>
              <PlanDonut plans={planItems} size={170} />
              <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: 8 }}>
                {planItems.map((p) => (
                  <div
                    key={p.name}
                    style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13 }}
                  >
                    <span
                      style={{
                        width: 10,
                        height: 10,
                        borderRadius: 2,
                        background: p.color,
                        flexShrink: 0,
                      }}
                    />
                    <span style={{ flex: 1 }}>{p.name}</span>
                    <span style={{ fontVariantNumeric: 'tabular-nums' }}>{p.count}</span>
                    <span style={{ width: 48, textAlign: 'right', fontSize: 11, color: 'var(--text-tertiary)' }}>
                      {((p.count / planTotal) * 100).toFixed(1)}%
                    </span>
                  </div>
                ))}
              </div>
            </div>
          ) : (
            <Empty description="暂无套餐授权" image={Empty.PRESENTED_IMAGE_SIMPLE} />
          )}
        </SectionCard>

        <SectionCard title="近 6 月续费率">
          {renewals.isPending ? (
            <Skeleton active title={false} paragraph={{ rows: 4 }} />
          ) : renewals.data ? (
            <>
              <div
                style={{
                  display: 'flex',
                  alignItems: 'flex-end',
                  gap: 8,
                  height: 140,
                  paddingTop: 18,
                }}
              >
                {renewals.data.months.map((m, i) => {
                  const rate = renewals.data.rates[i] ?? 0;
                  return (
                    <div
                      key={m}
                      style={{
                        flex: 1,
                        display: 'flex',
                        flexDirection: 'column',
                        alignItems: 'center',
                        gap: 6,
                        height: '100%',
                      }}
                    >
                      <div
                        style={{ flex: 1, width: '100%', display: 'flex', alignItems: 'flex-end' }}
                      >
                        <div
                          style={{
                            width: '100%',
                            // 品牌色渐变：浅端用 color-mix 调出品牌浅色，随主题换肤
                            background: `linear-gradient(to top, ${CHART_COLORS.brand}, color-mix(in srgb, var(--brand) 55%, var(--bg-elev)))`,
                            height: `${rate}%`,
                            borderRadius: '3px 3px 0 0',
                            position: 'relative',
                          }}
                        >
                          <span
                            style={{
                              position: 'absolute',
                              top: -18,
                              left: '50%',
                              transform: 'translateX(-50%)',
                              fontSize: 11,
                              fontWeight: 600,
                            }}
                          >
                            {rate}%
                          </span>
                        </div>
                      </div>
                      <span style={{ fontSize: 11, color: 'var(--text-tertiary)' }}>{Number(m.slice(5))}月</span>
                    </div>
                  );
                })}
              </div>
              <div
                style={{
                  fontSize: 12,
                  color: 'var(--text-tertiary)',
                  marginTop: 12,
                  borderTop: '1px dashed var(--divider)',
                  paddingTop: 10,
                  display: 'flex',
                  gap: 16,
                }}
              >
                <span>
                  临近到期{' '}
                  <b style={{ color: CHART_COLORS.warning }}>{renewals.data.expiringSoon}</b>
                </span>
                <span>
                  本月已续费{' '}
                  <b style={{ color: CHART_COLORS.success }}>{renewals.data.renewedThisMonth}</b>
                </span>
                <span>
                  本月加购 <b>{renewals.data.addonThisMonth}</b>
                </span>
              </div>
            </>
          ) : (
            <Empty description="暂无续费数据" image={Empty.PRESENTED_IMAGE_SIMPLE} />
          )}
        </SectionCard>
      </div>

      {/* 资源 + 健康度 + API/Webhook（进视口再取数） */}
      <div
        ref={resourceRowRef}
        style={{ display: 'grid', gridTemplateColumns: '1.2fr 0.8fr 1fr', gap: 16 }}
      >
        <SectionCard title="资源使用">
          {resources.isPending ? (
            <Skeleton active title={false} paragraph={{ rows: 4 }} />
          ) : resources.data ? (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
              <div>
                <div style={{ display: 'flex', alignItems: 'baseline', gap: 8, marginBottom: 8 }}>
                  <span style={{ fontSize: 12, color: 'var(--text-secondary)' }}>文件总大小</span>
                  <div style={{ flex: 1 }} />
                  <span style={{ fontSize: 18, fontWeight: 600 }}>
                    {formatBytes(resources.data.totalSize)}
                  </span>
                  <span style={{ fontSize: 11, color: 'var(--text-tertiary)' }}>
                    共 {resources.data.totalFiles.toLocaleString()} 文件
                  </span>
                </div>
                <div
                  style={{
                    display: 'flex',
                    height: 14,
                    borderRadius: 3,
                    overflow: 'hidden',
                    border: '1px solid var(--border)',
                  }}
                >
                  <div
                    style={{ width: `${platformPct}%`, background: CHART_COLORS.brand }}
                    title="平台 OSS"
                  />
                  <div
                    style={{ width: `${100 - platformPct}%`, background: CHART_COLORS.cyan }}
                    title="租户自有 OSS"
                  />
                </div>
                <div
                  style={{ display: 'flex', gap: 16, marginTop: 8, fontSize: 12, color: 'var(--text-secondary)' }}
                >
                  <span>平台 · {formatBytes(resources.data.platformSize)}</span>
                  <span>租户自有 · {formatBytes(resources.data.tenantOssSize)}</span>
                </div>
              </div>
              <div
                style={{
                  borderTop: '1px dashed var(--divider)',
                  paddingTop: 12,
                  display: 'grid',
                  gridTemplateColumns: '1fr 1fr',
                  gap: 12,
                }}
              >
                <div>
                  <div style={{ fontSize: 12, color: 'var(--text-tertiary)' }}>占用 Top 5 租户</div>
                  <div style={{ marginTop: 6, display: 'flex', flexDirection: 'column', gap: 4 }}>
                    {resources.data.topTenants.length ? (
                      resources.data.topTenants.map((t) => (
                        <div key={t.tenantId} style={{ display: 'flex', fontSize: 12 }}>
                          <span
                            style={{
                              flex: 1,
                              overflow: 'hidden',
                              textOverflow: 'ellipsis',
                              whiteSpace: 'nowrap',
                            }}
                          >
                            {String(t.tenantId ?? '') === '0' || (t.tenantName ?? '') === '0' ? (
                              '平台级文件（无归属租户）'
                            ) : (
                              <>
                                {t.tenantName}
                                {t.tenantDeleted && (
                                  <span style={{ color: 'var(--text-tertiary)' }}>（已删除）</span>
                                )}
                              </>
                            )}
                          </span>
                          <span style={{ color: 'var(--text-tertiary)' }}>{formatBytes(t.size)}</span>
                        </div>
                      ))
                    ) : (
                      <span style={{ fontSize: 12, color: 'var(--text-disabled)' }}>暂无</span>
                    )}
                  </div>
                </div>
                <div>
                  <div style={{ fontSize: 12, color: 'var(--text-tertiary)' }}>近 30 天增量</div>
                  <div style={{ fontSize: 18, fontWeight: 600, marginTop: 6 }}>
                    + {formatBytes(resources.data.incrementThisPeriod)}
                  </div>
                </div>
              </div>
            </div>
          ) : (
            <Empty description="暂无资源统计" image={Empty.PRESENTED_IMAGE_SIMPLE} />
          )}
        </SectionCard>

        <SectionCard title="客户健康度概览">
          {health.isPending ? (
            <Skeleton active title={false} paragraph={{ rows: 3 }} />
          ) : health.data && healthTotal > 0 ? (
            <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
              <svg width="100" height="100" viewBox="0 0 100 100">
                <circle
                  cx="50"
                  cy="50"
                  r="38"
                  fill="none"
                  strokeWidth="14"
                  style={{ stroke: 'var(--divider)' }}
                />
                <circle
                  cx="50"
                  cy="50"
                  r="38"
                  fill="none"
                  stroke={CHART_COLORS.success}
                  strokeWidth="14"
                  strokeDasharray={`${(health.data.healthy / healthTotal) * circ} ${circ}`}
                  transform="rotate(-90 50 50)"
                />
                <circle
                  cx="50"
                  cy="50"
                  r="38"
                  fill="none"
                  stroke={CHART_COLORS.warning}
                  strokeWidth="14"
                  strokeDasharray={`${(health.data.warning / healthTotal) * circ} ${circ}`}
                  strokeDashoffset={`-${(health.data.healthy / healthTotal) * circ}`}
                  transform="rotate(-90 50 50)"
                />
                <circle
                  cx="50"
                  cy="50"
                  r="38"
                  fill="none"
                  stroke={CHART_COLORS.danger}
                  strokeWidth="14"
                  strokeDasharray={`${(health.data.danger / healthTotal) * circ} ${circ}`}
                  strokeDashoffset={`-${((health.data.healthy + health.data.warning) / healthTotal) * circ}`}
                  transform="rotate(-90 50 50)"
                />
              </svg>
              <div
                style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: 6, fontSize: 13 }}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                  <span
                    style={{
                      width: 8,
                      height: 8,
                      borderRadius: 4,
                      background: CHART_COLORS.success,
                    }}
                  />
                  <span style={{ flex: 1 }}>健康</span>
                  <b>{health.data.healthy}</b>
                </div>
                <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                  <span
                    style={{
                      width: 8,
                      height: 8,
                      borderRadius: 4,
                      background: CHART_COLORS.warning,
                    }}
                  />
                  <span style={{ flex: 1 }}>关注</span>
                  <b>{health.data.warning}</b>
                </div>
                <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                  <span
                    style={{
                      width: 8,
                      height: 8,
                      borderRadius: 4,
                      background: CHART_COLORS.danger,
                    }}
                  />
                  <span style={{ flex: 1 }}>危险</span>
                  <b style={{ color: CHART_COLORS.danger }}>{health.data.danger}</b>
                </div>
              </div>
            </div>
          ) : (
            <Empty description="暂无健康度快照" image={Empty.PRESENTED_IMAGE_SIMPLE} />
          )}
        </SectionCard>

        <SectionCard title="API · Webhook 概览">
          {apiWebhook.isPending ? (
            <Skeleton active title={false} paragraph={{ rows: 4 }} />
          ) : apiWebhook.data ? (
            <>
              <div
                style={{
                  display: 'grid',
                  gridTemplateColumns: '1fr 1fr',
                  gap: 10,
                  marginBottom: 12,
                }}
              >
                <div style={{ background: 'var(--bg-muted)', borderRadius: 6, padding: 10 }}>
                  <div style={{ fontSize: 11, color: 'var(--text-tertiary)' }}>API 调用 (7d)</div>
                  <div style={{ fontSize: 18, fontWeight: 600, marginTop: 2 }}>
                    {apiWebhook.data.apiCalls7d.toLocaleString()}
                  </div>
                  <div
                    style={{
                      fontSize: 11,
                      // 有错误才染警示色，零错误保持中性，避免绿色被误读为「错误是好事」
                      color:
                        apiWebhook.data.apiErrors7d > 0
                          ? CHART_COLORS.danger
                          : 'var(--text-tertiary)',
                      marginTop: 2,
                    }}
                  >
                    错误 {apiWebhook.data.apiErrors7d}
                  </div>
                </div>
                <div style={{ background: 'var(--bg-muted)', borderRadius: 6, padding: 10 }}>
                  <div style={{ fontSize: 11, color: 'var(--text-tertiary)' }}>Webhook 推送 (7d)</div>
                  <div style={{ fontSize: 18, fontWeight: 600, marginTop: 2 }}>
                    {apiWebhook.data.webhookPushes7d.toLocaleString()}
                  </div>
                  <div style={{ fontSize: 11, color: CHART_COLORS.warning, marginTop: 2 }}>
                    成功率{' '}
                    {apiWebhook.data.webhookPushes7d > 0
                      ? `${apiWebhook.data.webhookSuccessRate}%`
                      : '—'}
                  </div>
                </div>
              </div>
              <div style={{ fontSize: 11, color: 'var(--text-tertiary)', marginBottom: 6 }}>
                Webhook 失败率 Top 4
              </div>
              {apiWebhook.data.topFailures.length ? (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                  {apiWebhook.data.topFailures.map((w) => (
                    <div
                      key={`${w.webhookId}-${w.eventType}`}
                      style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 12 }}
                    >
                      <span
                        style={{
                          flex: 1,
                          overflow: 'hidden',
                          textOverflow: 'ellipsis',
                          whiteSpace: 'nowrap',
                          color: 'var(--text-secondary)',
                        }}
                      >
                        {w.webhookName ?? `#${w.webhookId}`} · {w.eventType}
                      </span>
                      <span
                        style={{
                          color: w.successRate < 90 ? CHART_COLORS.danger : CHART_COLORS.warning,
                          fontWeight: 600,
                        }}
                      >
                        {w.successRate}%
                      </span>
                    </div>
                  ))}
                </div>
              ) : (
                <span style={{ fontSize: 12, color: 'var(--text-disabled)' }}>近 7 天无失败推送</span>
              )}
            </>
          ) : (
            <Empty description="暂无 API / Webhook 数据" image={Empty.PRESENTED_IMAGE_SIMPLE} />
          )}
        </SectionCard>
      </div>

      {/* 数据口径说明 */}
      <div
        style={{
          padding: '12px 16px',
          background: 'var(--bg-muted)',
          border: '1px dashed var(--border)',
          borderRadius: 6,
          fontSize: 12,
          color: 'var(--text-secondary)',
          display: 'flex',
          gap: 24,
          flexWrap: 'wrap',
        }}
      >
        <span>数据口径：</span>
        <span>实时聚合现有业务/日志表，按当前账号数据范围（OPS_OWNED 仅看归属租户）收敛</span>
        <span style={{ color: 'var(--text-tertiary)' }}>· DAU 按登录去重 · 模块/续费/资源近 30 天口径</span>
      </div>
    </div>
  );
}
