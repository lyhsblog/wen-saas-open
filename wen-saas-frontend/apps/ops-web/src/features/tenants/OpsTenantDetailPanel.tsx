import { useState } from 'react';
import { Button, Dropdown, Spin, Tabs, Tooltip, Typography } from 'antd';
import {
  CloseOutlined,
  MoreOutlined,
  LoginOutlined,
  PauseCircleOutlined,
  PlayCircleOutlined,
  TeamOutlined,
  CalendarOutlined,
  RiseOutlined,
} from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { opsTenantApi } from '@wen-saas/api-client';
import { ErrorState, formatDate } from '@wen-saas/shared-ui';
import { http } from '../../lib/http';
import { statusBadgeOf } from './utils/statusOf';
import { StatusPill } from '@wen-saas/shared-ui';
import { ImpersonateModal } from './modals/ImpersonateModal';
import { UpdateStatusModal } from './modals/UpdateStatusModal';
import { TransferMainAdminModal } from './modals/TransferMainAdminModal';
import { TabBasic } from './tabs/TabBasic';
import { TabMainAdmin } from './tabs/TabMainAdmin';
import { TabPlans } from './tabs/TabPlans';
import { TabSeatAddons } from './tabs/TabSeatAddons';
import { TabEvents } from './tabs/TabEvents';
import { TabUsage } from './tabs/TabUsage';
import { TabAuditLogs } from './tabs/TabAuditLogs';
import { tenantWebHost } from '../../lib/tenantWebUrl';

type Props = {
  tenantId: string;
  tab: string;
  onTabChange: (tab: string) => void;
  onClose: () => void;
};

function TenantAvatar({ name }: { name: string }) {
  const initials = name.slice(0, 2);
  return (
    <div
      style={{
        width: 64,
        height: 64,
        borderRadius: 'var(--r-md)',
        background: 'linear-gradient(135deg, var(--brand), var(--brand-hover))',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        color: '#fff',
        fontWeight: 700,
        fontSize: 22,
        letterSpacing: 1,
        flexShrink: 0,
        boxShadow: 'var(--shadow-md)',
      }}
    >
      {initials}
    </div>
  );
}

function fmtDateOnly(iso: string | null | undefined): string {
  return formatDate(iso);
}

function daysUntil(iso: string | null | undefined): number | null {
  if (!iso) return null;
  const ms = new Date(iso).getTime() - Date.now();
  return Math.ceil(ms / 86_400_000);
}

type KpiTone = 'brand' | 'green' | 'amber' | 'red' | 'purple' | 'gray';

const TONE_COLORS: Record<KpiTone, { bg: string; fg: string }> = {
  brand: { bg: 'var(--brand-bg)', fg: 'var(--brand)' },
  green: { bg: 'var(--success-bg)', fg: 'var(--success)' },
  amber: { bg: 'var(--warning-bg)', fg: 'var(--warning)' },
  red: { bg: 'var(--danger-bg)', fg: 'var(--danger)' },
  purple: { bg: 'var(--purple-bg)', fg: 'var(--purple)' },
  gray: { bg: 'var(--bg-muted)', fg: 'var(--text-tertiary)' },
};

function KpiCard({
  icon,
  label,
  value,
  sub,
  tone = 'gray',
}: {
  icon: React.ReactNode;
  label: string;
  value: string;
  sub?: string | undefined;
  tone?: KpiTone;
}) {
  const c = TONE_COLORS[tone];
  return (
    <div
      style={{
        padding: '14px 16px',
        background: 'var(--bg-elev)',
        borderRadius: 'var(--r-md)',
        border: '1px solid var(--border)',
        display: 'flex',
        alignItems: 'flex-start',
        gap: 12,
      }}
    >
      <div
        style={{
          width: 36,
          height: 36,
          borderRadius: 8,
          background: c.bg,
          color: c.fg,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          fontSize: 18,
          flexShrink: 0,
        }}
      >
        {icon}
      </div>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ fontSize: 12, color: 'var(--text-tertiary)', marginBottom: 4 }}>{label}</div>
        <div
          style={{
            fontSize: 18,
            fontWeight: 600,
            color: 'var(--text)',
            fontVariantNumeric: 'tabular-nums',
            lineHeight: 1.2,
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
          }}
        >
          {value}
        </div>
        {sub && (
          <div style={{ fontSize: 11, color: 'var(--text-tertiary)', marginTop: 2 }}>{sub}</div>
        )}
      </div>
    </div>
  );
}

export function OpsTenantDetailPanel({ tenantId, tab, onTabChange, onClose }: Props) {
  const [impersonateOpen, setImpersonateOpen] = useState(false);
  const [statusModalTarget, setStatusModalTarget] = useState<0 | 1 | null>(null);
  const [transferOpen, setTransferOpen] = useState(false);

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['ops', 'tenant', tenantId],
    queryFn: () => opsTenantApi(http).getTenantDetail(tenantId),
    enabled: !!tenantId,
    retry: false,
  });

  /** OPS-T-009：成员数/30 天活跃/登录趋势。详情打开即拉一次，KPI 卡与"使用情况"Tab 共用。 */
  const {
    data: usage,
    isError: usageError,
    refetch: refetchUsage,
  } = useQuery({
    queryKey: ['ops', 'tenant', tenantId, 'usage'],
    queryFn: () => opsTenantApi(http).getTenantUsage(tenantId),
    enabled: !!tenantId,
    retry: false,
  });

  if (isLoading) {
    return (
      <div
        style={{
          flex: 1,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          borderLeft: '1px solid var(--divider)',
        }}
      >
        <Spin size="large" />
      </div>
    );
  }

  if (isError || !data) {
    const msg = error instanceof Error ? error.message : '租户不存在或已删除';
    return (
      <div
        style={{
          flex: 1,
          display: 'flex',
          flexDirection: 'column',
          borderLeft: '1px solid var(--divider)',
          padding: 24,
        }}
      >
        <Button
          icon={<CloseOutlined />}
          aria-label="关闭"
          onClick={onClose}
          style={{ alignSelf: 'flex-end', marginBottom: 16 }}
        />
        <ErrorState
          status="404"
          title="找不到该租户"
          description={msg}
          primaryAction={{ label: '关闭', onClick: onClose }}
        />
      </div>
    );
  }

  const { tenant, mainAdmin, plans, seatAddons } = data;
  const badge = statusBadgeOf(tenant);

  // 头部「更多」= 结构/状态操作。名称/备注等字段编辑在基本信息 tab 就地进行（评审 #17 分工明确）。
  const moreMenuItems = [
    { key: 'transfer', label: '重新指定主管理员', onClick: () => setTransferOpen(true) },
  ];

  const isPaused = tenant.status === 0;
  const isExpired = tenant.status === 2;

  const tabItems = [
    {
      key: 'basic',
      label: '基本信息',
      children: <TabBasic tenant={tenant} />,
    },
    {
      key: 'main-admin',
      label: '主管理员',
      children: (
        <TabMainAdmin
          tenantId={tenant.id}
          tenantCode={tenant.code}
          tenantName={tenant.name}
          mainAdmin={mainAdmin}
        />
      ),
    },
    {
      key: 'plans',
      label: '套餐包',
      children: <TabPlans tenantId={tenant.id} plans={plans} />,
    },
    {
      key: 'seat-addons',
      label: '加购席位',
      children: <TabSeatAddons tenantId={tenant.id} seatAddons={seatAddons} />,
    },
    {
      key: 'finance',
      label: '账单与变更历史',
      children: <TabEvents tenantId={tenant.id} />,
    },
    {
      key: 'usage',
      label: '使用情况',
      children: <TabUsage usage={usage} isError={usageError} onRetry={() => void refetchUsage()} />,
    },
    {
      key: 'audit',
      label: '操作日志',
      children: <TabAuditLogs tenantId={tenant.id} />,
    },
  ];

  return (
    <div
      style={{
        flex: 1,
        display: 'flex',
        flexDirection: 'column',
        borderLeft: '1px solid var(--divider)',
        overflow: 'hidden',
      }}
    >
      {/* Header */}
      <div
        style={{
          padding: '20px 24px 16px',
          borderBottom: '1px solid var(--divider)',
          flexShrink: 0,
        }}
      >
        <div style={{ display: 'flex', alignItems: 'flex-start', gap: 16 }}>
          <TenantAvatar name={tenant.name} />
          <div style={{ flex: 1, minWidth: 0 }}>
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 10,
                marginBottom: 6,
                flexWrap: 'wrap',
              }}
            >
              <span style={{ fontSize: 20, fontWeight: 700, color: 'var(--text)' }}>
                {tenant.name}
              </span>
              <StatusPill badge={badge} />
              {tenant.tenantNo && (
                <span
                  style={{
                    fontFamily: 'var(--font-mono)',
                    fontSize: 12,
                    color: 'var(--text-tertiary)',
                    background: 'var(--bg-muted)',
                    padding: '1px 8px',
                    borderRadius: 4,
                  }}
                >
                  {tenant.tenantNo}
                </span>
              )}
            </div>
            <div
              style={{
                fontSize: 12,
                color: 'var(--text-tertiary)',
                display: 'flex',
                gap: 12,
                flexWrap: 'wrap',
              }}
            >
              <span>
                <Typography.Text
                  copyable={{ text: tenantWebHost(tenant.code), tooltips: ['复制', '已复制'] }}
                >
                  <code style={{ fontFamily: 'var(--font-mono)' }}>
                    {tenantWebHost(tenant.code)}
                  </code>
                </Typography.Text>
              </span>
              <span>·</span>
              <span>创建于 {fmtDateOnly(tenant.createdAt)}</span>
              <span>·</span>
              <span>
                最后活跃{' '}
                {tenant.lastActiveAt ? (
                  fmtDateOnly(tenant.lastActiveAt)
                ) : (
                  <span style={{ color: 'var(--text-quaternary)' }}>—</span>
                )}
              </span>
            </div>
          </div>
          {/* Action buttons：原型口径 [代登录 编辑 ✕]，暂停/启用并入「编辑」下拉。 */}
          <div style={{ display: 'flex', gap: 6, alignItems: 'center', flexShrink: 0 }}>
            {/* 代登录禁用时按原因给 Tooltip 解释：无主管理员 / 租户已暂停 */}
            <Tooltip
              title={
                !mainAdmin
                  ? '该租户暂无主管理员'
                  : isPaused
                    ? '租户已暂停，恢复后才能代登录'
                    : undefined
              }
            >
              <Button
                icon={<LoginOutlined />}
                size="small"
                type="primary"
                disabled={!mainAdmin || isPaused}
                onClick={() => setImpersonateOpen(true)}
              >
                代登录
              </Button>
            </Tooltip>
            <Dropdown
              menu={{
                items: [
                  ...moreMenuItems,
                  { type: 'divider' as const },
                  isPaused
                    ? {
                        key: 'enable',
                        label: '启用租户',
                        icon: <PlayCircleOutlined />,
                        onClick: () => setStatusModalTarget(1),
                      }
                    : {
                        key: 'pause',
                        label: '暂停租户',
                        icon: <PauseCircleOutlined />,
                        danger: true,
                        disabled: isExpired,
                        onClick: () => setStatusModalTarget(0),
                      },
                ],
              }}
              trigger={['click']}
            >
              <Button icon={<MoreOutlined />} size="small">
                更多
              </Button>
            </Dropdown>
            <Button icon={<CloseOutlined />} size="small" aria-label="关闭" onClick={onClose} />
          </div>
        </div>

        {/* 3 KPI 卡：成员数 / 到期时间 / 本月活跃。存储实时用量未开放，暂不占黄金位（评审 #16）。 */}
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(3, 1fr)',
            gap: 12,
            marginTop: 18,
          }}
        >
          <KpiCard
            icon={<TeamOutlined />}
            label="成员数"
            value={
              usage
                ? `${usage.memberCount}${tenant.memberCap != null ? ` / ${tenant.memberCap}` : ''}`
                : '—'
            }
            sub={tenant.memberCap == null ? '不限上限' : undefined}
            tone="brand"
          />
          <KpiCard
            icon={<CalendarOutlined />}
            label="到期时间"
            value={tenant.expireAt ? fmtDateOnly(tenant.expireAt) : '永久'}
            sub={(() => {
              const d = daysUntil(tenant.expireAt);
              if (d == null) return undefined;
              if (d < 0) return `已过期 ${-d} 天`;
              if (d <= 7) return `剩 ${d} 天`;
              if (d <= 30) return `剩 ${d} 天`;
              return `剩 ${d} 天`;
            })()}
            tone={(() => {
              const d = daysUntil(tenant.expireAt);
              if (d == null) return 'gray';
              if (d < 0) return 'red';
              if (d <= 7) return 'red';
              if (d <= 30) return 'amber';
              return 'gray';
            })()}
          />
          <KpiCard
            icon={<RiseOutlined />}
            label="本月活跃"
            value={usage ? `${usage.activeMembers30d} 人` : '—'}
            sub="近 30 天"
            tone="purple"
          />
        </div>
      </div>

      {/* Tabs */}
      <div style={{ flex: 1, overflow: 'auto', padding: '0 24px 24px' }}>
        <Tabs
          activeKey={tab}
          onChange={onTabChange}
          items={tabItems}
          size="small"
          style={{ marginTop: 0 }}
        />
      </div>

      {/* Modals */}
      {mainAdmin && (
        <ImpersonateModal
          open={impersonateOpen}
          tenantId={tenant.id}
          tenantCode={tenant.code}
          tenantName={tenant.name}
          mainAdminUserId={mainAdmin.userId}
          mainAdminUsername={mainAdmin.username}
          onClose={() => setImpersonateOpen(false)}
        />
      )}
      {statusModalTarget !== null && (
        <UpdateStatusModal
          open
          tenantId={tenant.id}
          tenantName={tenant.name}
          targetStatus={statusModalTarget}
          onClose={() => setStatusModalTarget(null)}
        />
      )}
      {transferOpen && (
        <TransferMainAdminModal
          open={transferOpen}
          tenantId={tenant.id}
          tenantCode={tenant.code}
          tenantName={tenant.name}
          currentAdminUsername={mainAdmin?.username ?? ''}
          onClose={() => setTransferOpen(false)}
        />
      )}
    </div>
  );
}
