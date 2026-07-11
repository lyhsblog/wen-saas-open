/**
 * 平台监控看板自绘 SVG 图表（OpmSparkArea / OpmTrendChart / OpmPlanDonut，
 * 纯展示、无图表库依赖）。颜色常量 / 工具见 chartUtils.ts。
 */
import { CHART_COLORS, niceYScale, type DonutItem } from './chartUtils';

type SparkProps = { values: number[]; color?: string; w?: number; h?: number };

export function SparkArea({ values, color = CHART_COLORS.brand, w = 240, h = 36 }: SparkProps) {
  if (!values || values.length === 0) {
    return null;
  }
  const max = Math.max(...values);
  const min = Math.min(...values);
  const range = max - min || 1;
  const step = w / (values.length - 1 || 1);
  const pts = values.map((p, i) => [i * step, h - ((p - min) / range) * (h - 4) - 2] as const);
  const line = pts.map(([x, y]) => `${x},${y}`).join(' ');
  const area = `M 0,${h} L ` + pts.map(([x, y]) => `${x},${y}`).join(' L ') + ` L ${w},${h} Z`;
  const last = pts[pts.length - 1]!;
  return (
    <svg width={w} height={h} style={{ display: 'block' }}>
      <path d={area} fill={color} opacity={0.12} />
      <polyline points={line} fill="none" stroke={color} strokeWidth={1.6} strokeLinejoin="round" />
      <circle cx={last[0]} cy={last[1]} r={2.5} fill={color} />
    </svg>
  );
}

type TrendProps = { dates: string[]; y1: number[]; y2: number[]; c1: string; c2: string };

export function TrendChart({ dates, y1, y2, c1, c2 }: TrendProps) {
  const W = 720;
  const H = 220;
  const padL = 40;
  const padR = 12;
  const padT = 16;
  const padB = 28;
  const innerW = W - padL - padR;
  const innerH = H - padT - padB;
  const n = y1.length;
  const { niceMax: max, tickCount: ticks } = niceYScale(Math.max(...y1, ...y2, 1));
  const min = 0;
  const step = innerW / (n - 1 || 1);
  const xs = y1.map((_, i) => padL + i * step);
  const yMap = (v: number) => padT + innerH - ((v - min) / (max - min || 1)) * innerH;
  const line1 = y1.map((v, i) => `${xs[i]},${yMap(v)}`).join(' ');
  const line2 = y2.map((v, i) => `${xs[i]},${yMap(v)}`).join(' ');
  const area1 =
    `M ${padL},${padT + innerH} L ` +
    y1.map((v, i) => `${xs[i]},${yMap(v)}`).join(' L ') +
    ` L ${padL + innerW},${padT + innerH} Z`;
  const labelStep = Math.max(1, Math.ceil(n / 8));
  return (
    <svg
      viewBox={`0 0 ${W} ${H}`}
      width="100%"
      height="240"
      preserveAspectRatio="none"
      style={{ display: 'block' }}
    >
      {Array.from({ length: ticks + 1 }, (_, i) => {
        const y = padT + (innerH / ticks) * i;
        const val = Math.round(max - (max - min) * (i / ticks));
        return (
          <g key={i}>
            <line
              x1={padL}
              y1={y}
              x2={W - padR}
              y2={y}
              stroke="var(--divider)"
              strokeDasharray="2 4"
            />
            <text x={padL - 6} y={y + 4} fontSize="10" fill="var(--text-tertiary)" textAnchor="end">
              {val >= 1000 ? (val / 1000).toFixed(1) + 'K' : val}
            </text>
          </g>
        );
      })}
      <path d={area1} fill={c1} opacity={0.1} />
      <polyline
        points={line1}
        fill="none"
        stroke={c1}
        strokeWidth={2}
        strokeLinejoin="round"
        strokeLinecap="round"
      />
      <polyline
        points={line2}
        fill="none"
        stroke={c2}
        strokeWidth={1.5}
        strokeDasharray="4 3"
        strokeLinejoin="round"
        strokeLinecap="round"
      />
      {xs.map((x, i) =>
        i % labelStep === 0 ? (
          <text
            key={i}
            x={x}
            y={H - 8}
            fontSize="10"
            fill="var(--text-tertiary)"
            textAnchor="middle"
          >
            {dates[i]}
          </text>
        ) : null,
      )}
    </svg>
  );
}

export function PlanDonut({ plans, size = 170 }: { plans: DonutItem[]; size?: number }) {
  const total = plans.reduce((s, p) => s + p.count, 0) || 1;
  const r = size / 2 - 12;
  const cx = size / 2;
  const cy = size / 2;
  const segs = plans.map((p, idx) => {
    const prior = plans.slice(0, idx).reduce((s, x) => s + x.count, 0);
    const start = (prior / total) * 2 * Math.PI;
    const end = ((prior + p.count) / total) * 2 * Math.PI;
    const x1 = cx + r * Math.sin(start);
    const y1 = cy - r * Math.cos(start);
    const x2 = cx + r * Math.sin(end);
    const y2 = cy - r * Math.cos(end);
    const large = end - start > Math.PI ? 1 : 0;
    return {
      d: `M ${cx} ${cy} L ${x1} ${y1} A ${r} ${r} 0 ${large} 1 ${x2} ${y2} Z`,
      color: p.color,
    };
  });
  return (
    <svg viewBox={`0 0 ${size} ${size}`} width={size} height={size} style={{ display: 'block' }}>
      {segs.map((s, i) => (
        <path key={i} d={s.d} fill={s.color} />
      ))}
      <circle cx={cx} cy={cy} r={r * 0.58} fill="var(--bg-elev)" />
      <text x={cx} y={cy - 4} fontSize="20" fontWeight={700} textAnchor="middle" fill="var(--text)">
        {total}
      </text>
      <text x={cx} y={cy + 14} fontSize="11" textAnchor="middle" fill="var(--text-tertiary)">
        活跃订阅
      </text>
    </svg>
  );
}
