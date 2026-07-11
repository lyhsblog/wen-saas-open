import { useMemo } from 'react';
import { App, Empty, Skeleton } from 'antd';
import {
  AppstoreOutlined,
  CheckSquareOutlined,
  MessageOutlined,
  NotificationOutlined,
  ReloadOutlined,
  RightOutlined,
} from '@ant-design/icons';
import { useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router';
import { CountChip, formatTodayOrDate, StatusPill, toneBadge } from '@wen-saas/shared-ui';
import { useCapabilities, type MenuNode } from '@wen-saas/permissions';
import type {
  TenantWorkbenchAnnouncementCard,
  TenantWorkbenchBusinessCard,
  TenantWorkbenchMessageCard,
  TenantWorkbenchPlanExpiry,
  TenantWorkbenchTodoCard,
} from '@wen-saas/api-client';
import { resolveIcon } from '../../lib/iconMap';
import { tenantPath, useTenantKey } from '../../lib/tenant-path';
import { WaBtn, WaCard, WaPage, WaPageHead } from '../../components/wa';
import { WorkbenchStickyCard } from '../stickyNote/WorkbenchStickyCard';
import { MfaNudgeBanner } from './MfaNudgeBanner';
import { useWorkbenchCards, useWorkbenchOverview, workbenchKeys } from './workbenchQueries';

/** 业务数据卡片占位宽度 → 6 列网格的 gridColumn。 */
const SPAN_COLS: Record<TenantWorkbenchBusinessCard['span'], string> = {
  THIRD: 'span 2',
  HALF: 'span 3',
  FULL: 'span 6',
};

const TONE_COLOR: Record<string, string> = {
  danger: 'var(--danger)',
  warning: 'var(--warning)',
  success: 'var(--success)',
  up: 'var(--success)',
  down: 'var(--danger)',
  muted: 'var(--text-tertiary)',
};

function toneColor(tone?: string | null): string {
  return (tone && TONE_COLOR[tone]) || 'var(--text-tertiary)';
}

export function WorkbenchPage() {
  const { message } = App.useApp();
  const qc = useQueryClient();
  const navigate = useNavigate();
  const tenantKey = useTenantKey();
  const caps = useCapabilities();

  const overview = useWorkbenchOverview();
  const cards = useWorkbenchCards();

  const go = (sub: string | null) => {
    if (sub) void navigate(tenantPath(tenantKey, sub));
  };

  const refreshing = overview.isFetching || cards.isFetching;
  const refresh = () => {
    void qc.invalidateQueries({ queryKey: workbenchKeys.all }).then(() => {
      message.success('已刷新');
    });
  };

  /**
   * 常用入口：从 capabilities.menus（后端 sys_menu 派生、已按权限+套餐门控）拍平取前 8 个页面入口。
   * 排除工作台自身与个人中心（个人中心经右上角用户卡进入、不挂侧栏，与 AppShell 一致）。
   */
  const quickEntries = useMemo(() => {
    const HIDDEN = new Set(['/workbench', '/profile']);
    const out: { path: string; label: string; icon: string | null }[] = [];
    const walk = (nodes: MenuNode[]) => {
      for (const n of nodes) {
        if (n.path && !HIDDEN.has(n.path)) out.push({ path: n.path, label: n.name, icon: n.icon });
        if (n.children?.length) walk(n.children);
      }
    };
    walk(caps?.menus ?? []);
    return out.slice(0, 8);
  }, [caps]);

  const o = overview.data;

  return (
    <WaPage>
      <WaPageHead
        title="工作台"
        hint={o ? `欢迎，${o.greeting.userName} · ${o.greeting.roleName}` : undefined}
        badges={<CountChip tone="brand">实时</CountChip>}
        actions={
          <WaBtn
            size="sm"
            icon={<ReloadOutlined spin={refreshing} />}
            onClick={refresh}
            disabled={refreshing}
          >
            刷新
          </WaBtn>
        }
      />

      <MfaNudgeBanner />

      {/* 套餐到期提醒（仅主管理员；severity=none 不显示） */}
      {o?.planExpiry && o.planExpiry.severity !== 'none' && (
        <PlanExpiryBanner expiry={o.planExpiry} />
      )}

      {/* 概览固定三卡 */}
      {overview.isLoading || !o ? (
        <Skeleton active paragraph={{ rows: 3 }} />
      ) : (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 16 }}>
          <TodoOverviewCard todo={o.todo} onItem={go} />
          <MessageOverviewCard message={o.message} onAll={() => go('/messages')} />
          <AnnouncementOverviewCard announcement={o.announcement} onAll={() => go('/messages')} />
        </div>
      )}

      {/* 常用入口 */}
      <WaCard title="常用入口">
        {quickEntries.length === 0 ? (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无可用入口" />
        ) : (
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(8, 1fr)', gap: 8 }}>
            {quickEntries.map((m) => (
              <button
                key={m.path}
                type="button"
                onClick={() => go(m.path)}
                style={{
                  display: 'flex',
                  flexDirection: 'column',
                  alignItems: 'center',
                  gap: 8,
                  padding: '14px 4px',
                  border: 'none',
                  background: 'transparent',
                  borderRadius: 'var(--r-md, 8px)',
                  cursor: 'pointer',
                }}
                onMouseEnter={(e) => (e.currentTarget.style.background = 'var(--bg-muted)')}
                onMouseLeave={(e) => (e.currentTarget.style.background = 'transparent')}
              >
                <span
                  style={{
                    width: 40,
                    height: 40,
                    borderRadius: 12,
                    background: 'var(--brand-bg)',
                    color: 'var(--brand)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                  }}
                >
                  {resolveIcon(m.icon ?? undefined) ?? <AppstoreOutlined />}
                </span>
                <span style={{ fontSize: 12, color: 'var(--text-secondary)' }}>{m.label}</span>
              </button>
            ))}
          </div>
        )}
      </WaCard>

      {/* 备忘便利贴（个人自助，与右下角悬浮抽屉共享数据） */}
      <WorkbenchStickyCard />

      {/* 业务数据卡片区（业务模块贡献的扩展点） */}
      <BusinessCardsSection loading={cards.isLoading} cards={cards.data ?? []} onAction={go} />
    </WaPage>
  );
}

function PlanExpiryBanner({ expiry }: { expiry: TenantWorkbenchPlanExpiry }) {
  const isRed = expiry.severity === 'red';
  const color = isRed ? 'var(--danger)' : 'var(--warning)';
  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 10,
        padding: '10px 16px',
        borderRadius: 'var(--r-md, 8px)',
        border: `1px solid ${color}`,
        background: isRed
          ? 'var(--danger-bg, rgba(255,77,79,0.08))'
          : 'var(--warning-bg, rgba(250,173,20,0.1))',
        fontSize: 13,
      }}
    >
      <StatusPill badge={toneBadge(isRed ? 'danger' : 'warning', isRed ? '紧急' : '提醒')} />
      <span>
        您的「{expiry.planName}」将在 <b style={{ color }}>{expiry.daysLeft}</b>{' '}
        天后到期，请及时联系客户经理续费。
      </span>
    </div>
  );
}

function OverviewCardShell({
  icon,
  title,
  count,
  children,
  onAll,
}: {
  icon: React.ReactNode;
  title: string;
  count: number;
  children: React.ReactNode;
  onAll?: () => void;
}) {
  return (
    <WaCard>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          marginBottom: 12,
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <span
            style={{
              width: 28,
              height: 28,
              borderRadius: 8,
              background: 'var(--brand-bg)',
              color: 'var(--brand)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            {icon}
          </span>
          <span style={{ fontWeight: 600, fontSize: 14 }}>{title}</span>
        </div>
        <span style={{ fontSize: 24, fontWeight: 600 }}>{count}</span>
      </div>
      <div style={{ fontSize: 13, color: 'var(--text-secondary)', minHeight: 44 }}>{children}</div>
      {onAll && (
        <div
          style={{ fontSize: 12, color: 'var(--brand)', marginTop: 10, cursor: 'pointer' }}
          onClick={onAll}
        >
          查看全部 →
        </div>
      )}
    </WaCard>
  );
}

function TodoOverviewCard({
  todo,
  onItem,
}: {
  todo: TenantWorkbenchTodoCard;
  onItem: (url: string | null) => void;
}) {
  return (
    <OverviewCardShell icon={<CheckSquareOutlined />} title="我的待办" count={todo.total}>
      {todo.items.length === 0 ? (
        <span style={{ color: 'var(--text-tertiary)' }}>暂无待办，享受清闲的一天</span>
      ) : (
        todo.items.map((it) => (
          <div
            key={it.label}
            onClick={() => onItem(it.actionUrl)}
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              padding: '4px 0',
              cursor: it.actionUrl ? 'pointer' : 'default',
            }}
          >
            <span>· {it.label}</span>
            <span className="wa-muted">{it.count}</span>
          </div>
        ))
      )}
    </OverviewCardShell>
  );
}

function MessageOverviewCard({
  message,
  onAll,
}: {
  message: TenantWorkbenchMessageCard;
  onAll: () => void;
}) {
  return (
    <OverviewCardShell
      icon={<MessageOutlined />}
      title="我的消息"
      count={message.unreadTotal}
      onAll={onAll}
    >
      {message.recent.length === 0 ? (
        <span style={{ color: 'var(--text-tertiary)' }}>暂无新消息</span>
      ) : (
        message.recent.slice(0, 2).map((m) => (
          <div key={m.id ?? m.title} className="wa-truncate" style={{ padding: '4px 0' }}>
            · {m.title ?? '（无标题）'}
          </div>
        ))
      )}
    </OverviewCardShell>
  );
}

function AnnouncementOverviewCard({
  announcement,
  onAll,
}: {
  announcement: TenantWorkbenchAnnouncementCard;
  onAll: () => void;
}) {
  const latest = announcement.latest;
  return (
    <OverviewCardShell
      icon={<NotificationOutlined />}
      title="系统公告"
      count={announcement.total}
      onAll={onAll}
    >
      {latest ? (
        <>
          <div className="wa-truncate" style={{ color: 'var(--text)', padding: '4px 0' }}>
            {latest.title ?? '（无标题）'}
          </div>
          <div style={{ fontSize: 12, color: 'var(--text-tertiary)' }}>
            {latest.from ?? '平台'} · {formatTodayOrDate(latest.time)}
          </div>
        </>
      ) : (
        <span style={{ color: 'var(--text-tertiary)' }}>暂无公告</span>
      )}
    </OverviewCardShell>
  );
}

function BusinessCardsSection({
  loading,
  cards,
  onAction,
}: {
  loading: boolean;
  cards: TenantWorkbenchBusinessCard[];
  onAction: (url: string | null) => void;
}) {
  return (
    <WaCard title="业务数据">
      {loading ? (
        <Skeleton active paragraph={{ rows: 3 }} />
      ) : cards.length === 0 ? (
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description="业务模块上线后将在此展示数据卡片"
          style={{ margin: '16px 0' }}
        />
      ) : (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(6, 1fr)', gap: 12 }}>
          {cards.map((c) => (
            <div key={c.code} style={{ gridColumn: SPAN_COLS[c.span] }}>
              <BusinessCard card={c} onAction={onAction} />
            </div>
          ))}
        </div>
      )}
    </WaCard>
  );
}

function BusinessCard({
  card,
  onAction,
}: {
  card: TenantWorkbenchBusinessCard;
  onAction: (url: string | null) => void;
}) {
  return (
    <div
      style={{
        border: '1px solid var(--border)',
        borderRadius: 'var(--r-md, 8px)',
        background: 'var(--bg-elev)',
        padding: 14,
        height: '100%',
      }}
    >
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          marginBottom: 10,
        }}
      >
        <span style={{ fontWeight: 600, fontSize: 14 }}>{card.name}</span>
        {card.actionUrl && (
          <RightOutlined
            style={{ fontSize: 12, color: 'var(--text-tertiary)', cursor: 'pointer' }}
            onClick={() => onAction(card.actionUrl)}
          />
        )}
      </div>
      <CardBody card={card} />
    </div>
  );
}

function CardBody({ card }: { card: TenantWorkbenchBusinessCard }) {
  const d = card.data;
  const empty = <span style={{ fontSize: 13, color: 'var(--text-tertiary)' }}>暂无数据</span>;
  if (!d) return empty;

  if (card.display === 'LIST') {
    if (!d.items || d.items.length === 0) return empty;
    return (
      <div style={{ fontSize: 13 }}>
        {d.items.map((it) => (
          <div
            key={it.label}
            style={{ display: 'flex', justifyContent: 'space-between', padding: '4px 0' }}
          >
            <span style={{ color: 'var(--text-secondary)' }}>{it.label}</span>
            <span style={{ fontWeight: 600, color: toneColor(it.tone) }}>{it.value}</span>
          </div>
        ))}
      </div>
    );
  }

  if (card.display === 'CHART') {
    if (!d.trend || d.trend.length === 0) return empty;
    return <MiniBars data={d.trend} />;
  }

  // NUMBER / NUMBER_TREND
  return (
    <div style={{ display: 'flex', alignItems: 'baseline', gap: 8 }}>
      <span style={{ fontSize: 24, fontWeight: 600 }}>{d.value ?? '—'}</span>
      {d.unit && <span style={{ fontSize: 13, color: 'var(--text-tertiary)' }}>{d.unit}</span>}
      {card.display === 'NUMBER_TREND' && d.delta && (
        <span style={{ fontSize: 12, color: toneColor(d.deltaTone) }}>{d.delta}</span>
      )}
    </div>
  );
}

function MiniBars({ data }: { data: number[] }) {
  const max = Math.max(...data, 1);
  return (
    <div style={{ display: 'flex', alignItems: 'flex-end', gap: 3, height: 56 }}>
      {data.map((v, i) => (
        <div
          key={i}
          style={{
            flex: 1,
            height: `${(v / max) * 100}%`,
            background: 'var(--brand)',
            opacity: 0.25 + (v / max) * 0.7,
            borderRadius: 2,
          }}
        />
      ))}
    </div>
  );
}
