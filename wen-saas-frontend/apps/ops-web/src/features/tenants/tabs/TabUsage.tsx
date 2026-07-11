import { Empty, Skeleton } from 'antd';
import type { OpsTenantUsageView } from '@wen-saas/api-client';
import { ErrorState } from '@wen-saas/shared-ui';

type Props = {
  usage: OpsTenantUsageView | undefined;
  /** usage 查询是否失败（查询在 DetailPanel，与 KPI 卡共用一次请求）。 */
  isError: boolean;
  onRetry: () => void;
};

/** 使用情况 Tab：成员数 / 30 天活跃 / 30 天每日登录趋势。OPS-T-009。 */
export function TabUsage({ usage, isError, onRetry }: Props) {
  if (isError) {
    return (
      <ErrorState title="使用数据加载失败" primaryAction={{ label: '重试', onClick: onRetry }} />
    );
  }

  if (!usage) {
    return <Skeleton active paragraph={{ rows: 4 }} style={{ padding: '16px 0' }} />;
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 20, padding: '8px 0' }}>
      {/* 顶部 2 个核心数 */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: 12 }}>
        <BigStat label="成员总数" value={usage.memberCount} suffix="人" />
        <BigStat label="近 30 天活跃成员" value={usage.activeMembers30d} suffix="人" />
      </div>

      {/* 30 天登录趋势小图 */}
      <div
        style={{
          padding: '16px 20px',
          background: 'var(--bg-elev)',
          border: '1px solid var(--border)',
          borderRadius: 'var(--r-md)',
        }}
      >
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'baseline',
            marginBottom: 12,
          }}
        >
          <span style={{ fontSize: 13, color: 'var(--text-secondary)', fontWeight: 600 }}>
            近 30 天登录趋势
          </span>
          <span style={{ fontSize: 11, color: 'var(--text-tertiary)' }}>
            共 {usage.loginTrend.reduce((s, p) => s + p.count, 0)} 次
          </span>
        </div>
        {usage.loginTrend.length === 0 ? (
          <Empty description="近 30 天无登录" image={Empty.PRESENTED_IMAGE_SIMPLE} />
        ) : (
          <TrendBars data={usage.loginTrend} />
        )}
      </div>
    </div>
  );
}

function BigStat({ label, value, suffix }: { label: string; value: number; suffix?: string }) {
  return (
    <div
      style={{
        padding: '16px 20px',
        background: 'var(--bg-elev)',
        border: '1px solid var(--border)',
        borderRadius: 'var(--r-md)',
      }}
    >
      <div style={{ fontSize: 12, color: 'var(--text-tertiary)', marginBottom: 6 }}>{label}</div>
      <div
        style={{
          fontSize: 28,
          fontWeight: 700,
          color: 'var(--text)',
          fontVariantNumeric: 'tabular-nums',
        }}
      >
        {value.toLocaleString('zh-CN')}
        {suffix && (
          <span
            style={{ fontSize: 13, color: 'var(--text-tertiary)', fontWeight: 400, marginLeft: 4 }}
          >
            {suffix}
          </span>
        )}
      </div>
    </div>
  );
}

/** 简单条形图（CSS bars，无外部依赖）。 */
function TrendBars({ data }: { data: { date: string; count: number }[] }) {
  const max = Math.max(1, ...data.map((d) => d.count));
  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'flex-end',
        gap: 4,
        height: 120,
        paddingBottom: 18,
        position: 'relative',
      }}
    >
      {data.map((p) => {
        const h = Math.round((p.count / max) * 100);
        return (
          <div
            key={p.date}
            title={`${p.date} · ${p.count} 次`}
            style={{
              flex: 1,
              minWidth: 6,
              height: `${h}%`,
              background:
                'linear-gradient(180deg, color-mix(in srgb, var(--brand) 55%, var(--bg-elev)), var(--brand))',
              borderRadius: '3px 3px 0 0',
              cursor: 'help',
              transition: 'opacity .15s',
            }}
            onMouseEnter={(e) => {
              e.currentTarget.style.opacity = '0.7';
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.opacity = '1';
            }}
          />
        );
      })}
      <div
        style={{
          position: 'absolute',
          left: 0,
          right: 0,
          bottom: 0,
          display: 'flex',
          justifyContent: 'space-between',
          fontSize: 10,
          color: 'var(--text-tertiary)',
          fontVariantNumeric: 'tabular-nums',
        }}
      >
        <span>{data[0]?.date.slice(5)}</span>
        <span>{data[data.length - 1]?.date.slice(5)}</span>
      </div>
    </div>
  );
}
