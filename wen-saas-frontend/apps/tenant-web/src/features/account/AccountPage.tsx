import { useState } from 'react';
import { App, Empty, Popover, Skeleton } from 'antd';
import {
  ReloadOutlined,
  RightOutlined,
  DownOutlined,
  CheckOutlined,
  SafetyCertificateOutlined,
  AppstoreOutlined,
  PhoneOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import {
  CountChip,
  DataTable,
  formatDate,
  formatDateTime,
  PLATFORM_BASE_CAPABILITIES,
  ServerError5xxPage,
  StatusPill,
  toneBadge,
  type SemanticTone,
} from '@wen-saas/shared-ui';
import {
  ApiError,
  type ChangeHistoryItem,
  type PlanItem,
  type SupportContact,
} from '@wen-saas/api-client';
import { accountApi } from './accountApi';
import { WaBtn, WaCard, WaPage, WaPageHead } from '../../components/wa';

const accountKeys = {
  overview: ['account', 'overview'] as const,
  history: (page: number) => ['account', 'history', page] as const,
};

/** 封装 Date.now() 到模块级，避免 render 内调用触发 purity 规则。 */
function remainingDays(expiresAt: string | null): number | null {
  if (!expiresAt) return null;
  return Math.ceil((new Date(expiresAt).getTime() - Date.now()) / 86400000);
}

type PlanStatus = 'forever' | 'expired' | 'expiring' | 'soon' | 'normal';

function planStatusOf(plan: PlanItem): { status: PlanStatus; days: number | null } {
  if (plan.readonly) return { status: 'expired', days: null };
  const days = remainingDays(plan.expiresAt);
  if (days == null) return { status: 'forever', days: null };
  if (days <= 7) return { status: 'expiring', days };
  if (days <= 30) return { status: 'soon', days };
  return { status: 'normal', days };
}

function statusBadge(plan: PlanItem) {
  const { status, days } = planStatusOf(plan);
  // 「永久有效」是品牌标记而非语义状态，走 CountChip brand；其余四档为状态语义，走 StatusPill。
  if (status === 'forever')
    return (
      <CountChip tone="brand">{plan.systemDefault ? '永久有效 · 系统标配' : '永久有效'}</CountChip>
    );
  if (status === 'expired')
    return <StatusPill badge={toneBadge('neutral', '已到期 · 数据只读保留')} />;
  if (status === 'expiring')
    return <StatusPill badge={toneBadge('danger', `临近到期 · 剩 ${days} 天`)} />;
  if (status === 'soon') return <StatusPill badge={toneBadge('warning', `剩 ${days} 天`)} />;
  return <StatusPill badge={toneBadge('success', `正常 · 剩 ${days} 天`)} />;
}

/** 变更类型 → 徽章语义色（对齐原型映射）。 */
function changeKind(type: string | null): SemanticTone {
  const map: Record<string, SemanticTone> = {
    开通: 'success',
    续费: 'success',
    加购: 'success',
    勾选: 'info',
    取消勾选: 'neutral',
    到期: 'neutral',
    暂停: 'warning',
  };
  return (type && map[type]) || 'neutral';
}

/** 租户后台 · 系统管理 · 套餐与账号。纯只读。 */
export function AccountPage() {
  const { message } = App.useApp();
  const qc = useQueryClient();
  const { data, isLoading, isError, error, isFetching, refetch } = useQuery({
    queryKey: accountKeys.overview,
    queryFn: () => accountApi.overview(),
  });
  const refresh = () => {
    void qc.invalidateQueries({ queryKey: ['account'] }).then(() => {
      message.success('已刷新');
    });
  };

  if (isError) {
    return (
      <WaPage>
        <ServerError5xxPage
          {...(error instanceof ApiError && error.requestId ? { requestId: error.requestId } : {})}
          onRetry={() => void refetch()}
        />
      </WaPage>
    );
  }
  if (isLoading || !data) {
    return (
      <WaPage>
        <WaCard>
          <Skeleton active />
        </WaCard>
      </WaPage>
    );
  }

  const totalSeats = (data.baseSeats ?? 0) + data.addonSeats;
  const usageRate = totalSeats > 0 ? Math.round((data.usedSeats / totalSeats) * 100) : 0;
  const nearestDays = remainingDays(data.nearestExpireAt);

  const stats: Array<{ k: string; v: string; mono?: boolean; sub?: string }> = [
    { k: '内部编号', v: data.tenantNo ?? '—', mono: true },
    {
      k: '套餐包数',
      v: `${data.packageCount} 个`,
      sub: data.expiringSoonCount > 0 ? `其中 ${data.expiringSoonCount} 个临近到期` : '均正常',
    },
    {
      k: '总席位',
      v: data.baseSeats == null ? '不限' : `${totalSeats}`,
      sub: `基础 ${data.baseSeats ?? '不限'} + 加购 ${data.addonSeats}`,
    },
    {
      k: '已使用',
      v: `${data.usedSeats}`,
      sub: data.baseSeats == null ? '在职成员数' : `占用率 ${usageRate}%`,
    },
    {
      k: '最近到期',
      v: data.nearestExpireAt ? formatDate(data.nearestExpireAt) : '全部永久',
      ...(nearestDays != null ? { sub: `剩 ${nearestDays} 天` } : {}),
    },
  ];

  return (
    <WaPage>
      <WaPageHead
        title="套餐与账号"
        badges={<CountChip>只读 · 续费 / 加购请联系客户经理</CountChip>}
        actions={
          <WaBtn
            size="sm"
            icon={<ReloadOutlined spin={isFetching} />}
            onClick={refresh}
            disabled={isFetching}
          >
            刷新
          </WaBtn>
        }
      />

      {/* 概况 */}
      <WaCard title="账户概况">
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(5, 1fr)', gap: 0 }}>
          {stats.map((s, i) => (
            <div
              key={s.k}
              style={{
                padding: '4px 20px',
                borderRight: i < 4 ? '1px solid var(--divider)' : 'none',
              }}
            >
              <div className="wa-stat-label">{s.k}</div>
              <div
                style={{
                  fontSize: 20,
                  fontWeight: 600,
                  marginTop: 4,
                  fontFamily: s.mono ? 'var(--font-mono)' : 'inherit',
                }}
              >
                {s.v}
              </div>
              {s.sub && (
                <div style={{ fontSize: 11, color: 'var(--text-tertiary)', marginTop: 2 }}>
                  {s.sub}
                </div>
              )}
            </div>
          ))}
        </div>
      </WaCard>

      {/* 当前套餐包 */}
      <WaCard
        title="当前套餐包"
        flush
        extra={
          <span style={{ fontSize: 12, color: 'var(--text-tertiary)' }}>点击行展开功能清单</span>
        }
      >
        {data.plans.length === 0 ? (
          <Empty description="暂无套餐" />
        ) : (
          <div style={{ margin: '-14px -20px' }}>
            <div style={{ padding: '0 20px' }}>
              {data.plans.map((pkg, i) => (
                <PlanRow
                  key={pkg.packageCode}
                  pkg={pkg}
                  last={i === data.plans.length - 1}
                  support={data.support}
                />
              ))}
            </div>
          </div>
        )}
      </WaCard>

      {/* 底座基础能力（所有套餐默认包含） */}
      <WaCard
        title="底座基础能力"
        extra={<StatusPill badge={toneBadge('success', '所有套餐默认包含')} />}
      >
        <div style={{ fontSize: 13, color: 'var(--text-secondary)', marginBottom: 12 }}>
          以下为平台框架级能力，开通即享、无需选配，与上方按套餐选配的业务模块相互独立。
        </div>
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fill, minmax(220px, 1fr))',
            gap: 8,
          }}
        >
          {PLATFORM_BASE_CAPABILITIES.map((cap) => (
            <div
              key={cap.name}
              style={{
                display: 'flex',
                alignItems: 'flex-start',
                gap: 8,
                padding: '8px 12px',
                background: 'var(--bg-muted)',
                borderRadius: 'var(--r-md)',
              }}
            >
              <CheckOutlined style={{ fontSize: 12, color: 'var(--success)', marginTop: 4 }} />
              <div>
                <div style={{ fontSize: 13, fontWeight: 500 }}>{cap.name}</div>
                <div style={{ fontSize: 11, color: 'var(--text-tertiary)' }}>{cap.desc}</div>
              </div>
            </div>
          ))}
        </div>
      </WaCard>

      {/* 加购席位 */}
      <WaCard title="加购席位" extra={<CountChip tone="brand">永久有效</CountChip>}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 24 }}>
          <div>
            <div className="wa-stat-label">累计加购</div>
            <div style={{ fontSize: 32, fontWeight: 600, color: 'var(--brand)' }}>
              {data.addonSeats}
            </div>
            <div style={{ fontSize: 12, color: 'var(--text-tertiary)' }}>个席位</div>
          </div>
          <div style={{ width: 1, height: 56, background: 'var(--divider)' }} />
          <div style={{ flex: 1, fontSize: 13, color: 'var(--text-secondary)' }}>
            加购席位永久有效，计入总席位上限。加购明细见下方「变更历史」，如需加购请联系您的客户经理。
          </div>
        </div>
      </WaCard>

      {/* 变更历史 */}
      <ChangeHistoryCard />

      {/* 客服联系信息 */}
      <WaCard>
        <div style={{ display: 'flex', alignItems: 'center', gap: 16, padding: '4px 0' }}>
          <div
            style={{
              width: 48,
              height: 48,
              borderRadius: 'var(--r-md)',
              background: 'var(--brand-bg)',
              color: 'var(--brand)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              flexShrink: 0,
              fontSize: 22,
            }}
          >
            <PhoneOutlined />
          </div>
          <div style={{ flex: 1 }}>
            <div style={{ fontSize: 14, fontWeight: 600, marginBottom: 4 }}>
              需要续费、加购、调整套餐？联系您的客户经理
            </div>
            <div style={{ fontSize: 13, color: 'var(--text-secondary)' }}>
              <span style={{ marginRight: 24 }}>
                联系电话：
                <b style={{ color: 'var(--text)', fontFamily: 'var(--font-mono)' }}>
                  {data.support.phone ?? '—'}
                </b>
              </span>
              <span>
                邮箱：<b style={{ color: 'var(--text)' }}>{data.support.email ?? '—'}</b>
              </span>
            </div>
          </div>
        </div>
      </WaCard>
    </WaPage>
  );
}

function PlanRow({
  pkg,
  last,
  support,
}: {
  pkg: PlanItem;
  last: boolean;
  support: SupportContact;
}) {
  const [open, setOpen] = useState(false);
  const { status } = planStatusOf(pkg);
  const bgRow =
    status === 'expired'
      ? 'var(--bg-muted)'
      : status === 'expiring'
        ? 'var(--warning-bg)'
        : 'transparent';
  const borderL =
    status === 'expiring'
      ? '3px solid var(--danger)'
      : status === 'forever'
        ? '3px solid var(--brand)'
        : status === 'soon'
          ? '3px solid var(--warning)'
          : '3px solid transparent';

  return (
    <div style={{ borderBottom: last ? 'none' : '1px solid var(--divider)' }}>
      <div
        onClick={() => setOpen((v) => !v)}
        style={{
          display: 'flex',
          alignItems: 'center',
          padding: '14px 12px',
          cursor: 'pointer',
          background: bgRow,
          borderLeft: borderL,
          opacity: status === 'expired' ? 0.7 : 1,
        }}
      >
        <span style={{ color: 'var(--text-tertiary)', marginRight: 8, fontSize: 12 }}>
          {open ? <DownOutlined /> : <RightOutlined />}
        </span>
        <div
          style={{
            width: 36,
            height: 36,
            borderRadius: 'var(--r-md)',
            // 套餐类型是分类不是状态，不上分类色；系统标配/自购的区分由徽章文案承担
            background: 'var(--brand-bg)',
            color: 'var(--brand)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            marginRight: 12,
            fontSize: 18,
          }}
        >
          {pkg.systemDefault ? <SafetyCertificateOutlined /> : <AppstoreOutlined />}
        </div>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ fontSize: 14, fontWeight: 600 }}>{pkg.name}</div>
          <div style={{ fontSize: 12, color: 'var(--text-tertiary)' }}>
            {pkg.modules.length > 0
              ? `含 ${pkg.modules.length} 项业务模块 · 底座基础能力默认包含`
              : '仅含底座基础能力 · 未选配额外业务模块'}
          </div>
        </div>
        <div
          style={{
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'flex-end',
            gap: 4,
            marginRight: 16,
          }}
        >
          {statusBadge(pkg)}
          <span style={{ fontSize: 12, color: 'var(--text-tertiary)' }}>
            {pkg.expiresAt ? formatDate(pkg.expiresAt) : '永久有效'}
          </span>
        </div>
        {status === 'expiring' && (
          <Popover
            trigger="click"
            title="联系客服办理续费"
            content={
              <div style={{ fontSize: 13, color: 'var(--text-secondary)' }}>
                <div style={{ marginBottom: 4 }}>
                  联系电话：
                  <b style={{ color: 'var(--text)', fontFamily: 'var(--font-mono)' }}>
                    {support.phone ?? '—'}
                  </b>
                </div>
                <div>
                  邮箱：<b style={{ color: 'var(--text)' }}>{support.email ?? '—'}</b>
                </div>
              </div>
            }
          >
            <WaBtn
              kind="primary"
              size="sm"
              icon={<PhoneOutlined />}
              onClick={(e) => e.stopPropagation()}
            >
              联系续费
            </WaBtn>
          </Popover>
        )}
      </div>
      {open && (
        <div
          style={{
            padding: '4px 56px 16px',
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fill, minmax(180px, 1fr))',
            gap: 6,
          }}
        >
          <div
            style={{
              gridColumn: '1 / -1',
              fontSize: 12,
              color: 'var(--text-tertiary)',
              marginBottom: 4,
            }}
          >
            业务模块 · {pkg.modules.length} 项（底座基础能力见下方卡片，所有套餐默认包含）
          </div>
          {pkg.modules.length === 0 ? (
            <div style={{ fontSize: 13, color: 'var(--text-tertiary)' }}>
              该套餐未选配额外业务模块，仅含平台底座基础能力
            </div>
          ) : (
            pkg.modules.map((m) => (
              <div
                key={m.moduleCode}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 6,
                  fontSize: 13,
                  color: status === 'expired' ? 'var(--text-tertiary)' : 'var(--text-secondary)',
                }}
              >
                <CheckOutlined
                  style={{
                    fontSize: 12,
                    color: status === 'expired' ? 'var(--text-tertiary)' : 'var(--success)',
                  }}
                />
                {m.moduleName}
              </div>
            ))
          )}
        </div>
      )}
    </div>
  );
}

function ChangeHistoryCard() {
  const [page, setPage] = useState(1);
  const { data, isLoading } = useQuery({
    queryKey: accountKeys.history(page),
    queryFn: () => accountApi.changeHistory(page, 10),
  });

  // 后端 Long 可能序列化为字符串，数值化避免 `total === 0` 判空失效（否则空列表显示「第 1-0 条」）
  const total = Number(data?.total ?? 0);
  const pageSize = Number(data?.pageSize ?? 10);

  const columns: ColumnsType<ChangeHistoryItem> = [
    {
      title: '时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 170,
      render: (v: string) => (
        <span className="wa-muted" style={{ fontFamily: 'var(--font-mono)', fontSize: 12 }}>
          {formatDateTime(v)}
        </span>
      ),
    },
    {
      title: '变更类型',
      key: 'kind',
      width: 100,
      render: (_: unknown, h) => (
        <StatusPill badge={toneBadge(changeKind(h.objectType), h.objectType ?? h.action)} />
      ),
    },
    {
      title: '变更对象',
      dataIndex: 'objectDesc',
      key: 'objectDesc',
      render: (v: string | null) => v ?? '—',
    },
    {
      title: '操作人',
      key: 'operator',
      width: 140,
      render: (_: unknown, h) => <span className="wa-muted">{h.operatorName ?? '系统'}</span>,
    },
  ];

  // 卡片内距归零，标题 / 内距 / 分页底栏全部由 DataTable 承载，避免双重留白。
  return (
    <WaCard padding={0}>
      <DataTable<ChangeHistoryItem>
        title="变更历史"
        columns={columns}
        dataSource={data?.items}
        loading={isLoading}
        simplePagination={{ total, page, pageSize, onChange: setPage }}
        empty={{ description: '暂无变更记录' }}
      />
    </WaCard>
  );
}
