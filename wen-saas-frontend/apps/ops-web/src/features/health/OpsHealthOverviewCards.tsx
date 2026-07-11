import { Spin } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { opsHealthApi } from '@wen-saas/api-client';
import type { HealthLevel, HealthOverview } from '@wen-saas/api-client';
import { http } from '../../lib/http';
import { formatDelta, healthLevelColor, HEALTH_LEVEL_LABEL } from './utils/healthMeta';

type CardSpec = {
  level: HealthLevel;
  count: number;
  delta: number;
};

function specs(o: HealthOverview): CardSpec[] {
  return [
    { level: 'healthy', count: o.healthy, delta: o.healthyDelta },
    { level: 'warning', count: o.warning, delta: o.warningDelta },
    { level: 'danger', count: o.danger, delta: o.dangerDelta },
  ];
}

export function OpsHealthOverviewCards() {
  const { data, isLoading } = useQuery({
    queryKey: ['ops', 'health', 'overview'],
    queryFn: () => opsHealthApi(http).overview(),
  });

  if (isLoading || !data) {
    return (
      <div style={{ padding: '24px 0', textAlign: 'center' }}>
        <Spin />
      </div>
    );
  }

  return (
    <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap' }}>
      {specs(data).map((c) => {
        const color = healthLevelColor(c.level);
        const delta = formatDelta(c.delta);
        return (
          <div
            key={c.level}
            style={{
              flex: '1 1 180px',
              minWidth: 180,
              background: 'var(--bg-elev)',
              border: '1px solid var(--border)',
              borderRadius: 'var(--r-md, 8px)',
              borderTop: `3px solid ${color}`,
              padding: '16px 20px',
            }}
          >
            <div style={{ fontSize: 13, color: 'var(--text-secondary)', marginBottom: 8 }}>
              {HEALTH_LEVEL_LABEL[c.level]}
            </div>
            <div style={{ display: 'flex', alignItems: 'baseline', gap: 10 }}>
              <span
                style={{ fontSize: 30, fontWeight: 700, color, fontVariantNumeric: 'tabular-nums' }}
              >
                {c.count}
              </span>
              <span
                style={{
                  fontSize: 13,
                  color:
                    delta.up === null
                      ? 'var(--text-tertiary)'
                      : delta.up
                        ? 'var(--success)'
                        : 'var(--danger)',
                }}
              >
                {delta.text}
              </span>
            </div>
          </div>
        );
      })}
    </div>
  );
}
