import { useState } from 'react';
import { Button, Empty, Spin, Tooltip } from 'antd';
import { CloseOutlined, LoginOutlined, RightOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router';
import { opsHealthApi, opsTenantApi } from '@wen-saas/api-client';
import type { HealthMetrics } from '@wen-saas/api-client';
import { http } from '../../lib/http';
import { StatusPill } from '@wen-saas/shared-ui';
import { ImpersonateModal } from '../tenants/modals/ImpersonateModal';
import { healthLevelBadge, healthLevelColor } from './utils/healthMeta';

type Props = {
  tenantId: string;
  onClose: () => void;
};

function metricRows(m: HealthMetrics): { label: string; value: string }[] {
  const rows: { label: string; value: string }[] = [];
  const active = m.monthlyActiveMembers;
  const total = m.totalMembers;
  if (total != null) {
    const pct = total > 0 && active != null ? Math.round((active / total) * 100) : 0;
    rows.push({ label: '月活成员', value: `${active ?? 0} / ${total} · ${pct}%` });
  }
  if (m.moduleAccessCount != null) {
    const chg = m.moduleAccessChangePct;
    const chgText = chg == null ? '' : `（环比 ${chg > 0 ? '+' : ''}${chg}%）`;
    rows.push({ label: '模块访问次数', value: `${m.moduleAccessCount} 次${chgText}` });
  }
  rows.push({ label: '数据增长', value: m.dataGrowth == null ? '—' : `${m.dataGrowth} 条新数据` });
  rows.push({ label: '工单', value: m.ticketCount == null ? '—' : String(m.ticketCount) });
  return rows;
}

export function OpsHealthDetailPanel({ tenantId, onClose }: Props) {
  const navigate = useNavigate();
  const [impersonateOpen, setImpersonateOpen] = useState(false);

  const { data, isLoading } = useQuery({
    queryKey: ['ops', 'health', 'detail', tenantId],
    queryFn: () => opsHealthApi(http).getDetail(tenantId),
  });

  // 代登录需租户编码 + 主管理员，按需拉租户详情复用模块 05 弹窗。
  const { data: tenant } = useQuery({
    queryKey: ['ops', 'tenant', 'detail', tenantId],
    queryFn: () => opsTenantApi(http).getTenantDetail(tenantId),
  });

  const header = (
    <div
      style={{
        padding: '14px 20px',
        borderBottom: '1px solid var(--divider)',
        display: 'flex',
        alignItems: 'center',
        gap: 8,
      }}
    >
      <span style={{ fontWeight: 600, fontSize: 15, flex: 1, minWidth: 0 }}>
        {data?.tenantName ?? tenant?.tenant.name ?? '租户健康度'}
      </span>
      <Button
        type="text"
        size="small"
        icon={<CloseOutlined />}
        onClick={onClose}
        aria-label="关闭"
      />
    </div>
  );

  let body: React.ReactNode;
  if (isLoading || !data) {
    body = (
      <div style={{ padding: 48, textAlign: 'center' }}>
        <Spin />
      </div>
    );
  } else {
    const color = healthLevelColor(data.level);
    body = (
      <div style={{ padding: 20, display: 'flex', flexDirection: 'column', gap: 24 }}>
        {/* 评分 */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <span
            style={{ fontSize: 40, fontWeight: 800, color, fontVariantNumeric: 'tabular-nums' }}
          >
            {data.score}
          </span>
          <StatusPill badge={healthLevelBadge(data.level)} />
        </div>

        {/* 风险信号 */}
        <section>
          <div style={{ fontSize: 13, fontWeight: 600, marginBottom: 10 }}>风险信号</div>
          {data.riskSignals.length === 0 ? (
            <span style={{ color: 'var(--text-tertiary)', fontSize: 13 }}>无风险信号</span>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              {data.riskSignals.map((s) => (
                <div
                  key={s.code}
                  style={{ display: 'flex', alignItems: 'center', fontSize: 13, gap: 8 }}
                >
                  <span style={{ width: 6, height: 6, borderRadius: '50%', background: color }} />
                  <span style={{ flex: 1 }}>{s.label}</span>
                  <span style={{ color: 'var(--danger)', fontVariantNumeric: 'tabular-nums' }}>
                    权重 -{s.weight}
                  </span>
                </div>
              ))}
            </div>
          )}
        </section>

        {/* 最近 30 天指标 */}
        <section>
          <div style={{ fontSize: 13, fontWeight: 600, marginBottom: 10 }}>最近 30 天指标</div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            {metricRows(data.metrics).map((row) => (
              <div
                key={row.label}
                style={{ display: 'flex', fontSize: 13, justifyContent: 'space-between' }}
              >
                <span style={{ color: 'var(--text-secondary)' }}>{row.label}</span>
                <span style={{ fontVariantNumeric: 'tabular-nums' }}>{row.value}</span>
              </div>
            ))}
          </div>
        </section>

        {/* 操作（纯跳转，不承担流程） */}
        <div style={{ display: 'flex', gap: 8 }}>
          <Tooltip title={tenant?.mainAdmin ? '' : '该租户暂无主管理员，无法代登录'}>
            <Button
              icon={<LoginOutlined />}
              disabled={!tenant?.mainAdmin}
              onClick={() => setImpersonateOpen(true)}
            >
              代登录
            </Button>
          </Tooltip>
          <Button
            type="primary"
            icon={<RightOutlined />}
            onClick={() => {
              void navigate(`/tenants?selected=${encodeURIComponent(tenantId)}&tab=basic`);
            }}
          >
            去租户详情
          </Button>
        </div>
      </div>
    );
  }

  return (
    <div
      style={{
        width: 420,
        flexShrink: 0,
        display: 'flex',
        flexDirection: 'column',
        background: 'var(--bg-elev)',
        overflow: 'hidden',
      }}
    >
      {header}
      <div style={{ flex: 1, overflowY: 'auto' }}>
        {data == null && !isLoading ? (
          <Empty description="租户不存在" style={{ padding: '48px 0' }} />
        ) : (
          body
        )}
      </div>
      {tenant?.mainAdmin && (
        <ImpersonateModal
          open={impersonateOpen}
          tenantId={tenantId}
          tenantCode={tenant.tenant.code}
          tenantName={tenant.tenant.name}
          mainAdminUserId={tenant.mainAdmin.userId}
          mainAdminUsername={tenant.mainAdmin.username}
          onClose={() => setImpersonateOpen(false)}
        />
      )}
    </div>
  );
}
