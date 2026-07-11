import { DotLoading, PullToRefresh } from 'antd-mobile';
import { useNavigate } from 'react-router';
import type { TenantWorkbenchBusinessCard } from '@wen-saas/api-client';
import { Card, Tag } from '../../components/ui';
import { formatRelative } from '../../lib/format';
import { moduleRegistry } from '../../lib/modules';
import { useUnreadSummary } from '../messages/messageQueries';
import { useStickyNotes } from '../stickyNote/stickyNoteQueries';
import { useWorkbenchOverview, useWorkbenchCards } from './workbenchQueries';

export function WorkspacePage() {
  const navigate = useNavigate();
  const overviewQ = useWorkbenchOverview();
  const cardsQ = useWorkbenchCards();
  // 未读数走 /unread-count 实时源（标已读后自动失效重拉 + 窗口聚焦刷新），
  // 与底部消息 tab 角标同源，避免只随工作台刷新才更新。
  const unreadQ = useUnreadSummary();
  const unreadTotal = unreadQ.data?.total ?? 0;
  const stickyQ = useStickyNotes();

  const go = (sub: string) => () => void navigate(`/${sub}`);

  const onRefresh = async () => {
    await Promise.all([
      overviewQ.refetch(),
      cardsQ.refetch(),
      unreadQ.refetch(),
      stickyQ.refetch(),
    ]);
  };

  const ov = overviewQ.data;
  const cards = (cardsQ.data ?? []).filter((c) => c.showOnH5);

  return (
    <div style={{ minHeight: '100%', background: 'var(--bg)', paddingBottom: 16 }}>
      <PullToRefresh onRefresh={onRefresh}>
        {/* 顶部用户条 */}
        <div
          style={{
            padding: '16px 16px 14px',
            background: 'linear-gradient(180deg, var(--brand) 0%, var(--brand-active) 100%)',
            color: '#fff',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <div style={{ flex: 1, minWidth: 0 }}>
              <div style={{ fontSize: 18, fontWeight: 600 }}>
                {ov ? `你好，${ov.greeting.userName}` : '加载中…'}
              </div>
              {ov && (
                <div style={{ fontSize: 12, opacity: 0.85, marginTop: 3 }}>
                  {ov.greeting.roleName}
                </div>
              )}
            </div>
            <div
              onClick={go('messages')}
              style={{
                position: 'relative',
                width: 38,
                height: 38,
                borderRadius: 19,
                background: 'rgba(255,255,255,0.16)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              <BellIcon />
              {unreadTotal > 0 && (
                <span
                  style={{
                    position: 'absolute',
                    top: 5,
                    right: 7,
                    width: 8,
                    height: 8,
                    borderRadius: 4,
                    // 未读标识统一 danger 红（与 TabBar 消息角标同色），白边用于渐变底上勾边
                    background: 'var(--danger, #dc2626)',
                    border: '1.5px solid #fff',
                  }}
                />
              )}
            </div>
          </div>
        </div>

        <div style={{ padding: 12 }}>
          {/* 套餐到期提醒（仅主管理员且临近） */}
          {ov?.planExpiry && ov.planExpiry.severity !== 'none' && (
            <Card
              style={{
                padding: '10px 12px',
                marginBottom: 12,
                display: 'flex',
                alignItems: 'center',
                gap: 8,
                background:
                  ov.planExpiry.severity === 'red'
                    ? 'var(--danger-bg, #fef2f2)'
                    : 'var(--warning-bg, #fff7ed)',
              }}
            >
              <div
                style={{
                  flex: 1,
                  fontSize: 12,
                  lineHeight: 1.6,
                  color:
                    ov.planExpiry.severity === 'red'
                      ? 'var(--danger, #dc2626)'
                      : 'var(--warning, #d97706)',
                }}
              >
                <b>{ov.planExpiry.planName}</b> 将于 <b>{ov.planExpiry.daysLeft} 天后</b>
                到期，请及时续费
              </div>
            </Card>
          )}

          {/* 统计三卡：待办 / 未读消息 / 便签（便签为个人自助，点击进便签页） */}
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: '1fr 1fr 1fr',
              gap: 10,
              marginBottom: 12,
            }}
          >
            {/* 加载中显示占位「—」而不是 0，避免误导 */}
            <StatCard
              label="待办"
              value={overviewQ.isLoading ? '—' : (ov?.todo.total ?? 0)}
              onClick={go('todos')}
            />
            <StatCard
              label="未读消息"
              value={unreadQ.isLoading ? '—' : unreadTotal}
              onClick={go('messages')}
            />
            <StatCard
              label="便签"
              value={stickyQ.isLoading ? '—' : (stickyQ.data?.length ?? 0)}
              onClick={go('sticky-notes')}
            />
          </div>

          {/* 公告 */}
          {ov?.announcement.latest && (
            <Card style={{ padding: '14px 16px', marginBottom: 12 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 6 }}>
                <Tag tone="warning">公告</Tag>
                <div style={{ flex: 1 }} />
                <span style={{ fontSize: 11, color: 'var(--text-tertiary)' }}>
                  {formatRelative(ov.announcement.latest.time)}
                </span>
              </div>
              <div style={{ fontSize: 14, fontWeight: 500, lineHeight: 1.5 }}>
                {ov.announcement.latest.title}
              </div>
              {ov.announcement.latest.from && (
                <div style={{ fontSize: 11, color: 'var(--text-tertiary)', marginTop: 4 }}>
                  {ov.announcement.latest.from}
                </div>
              )}
            </Card>
          )}

          {/* 业务数据卡片 */}
          {cards.length > 0 && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 10, marginBottom: 12 }}>
              {cards.map((c) => (
                <BusinessCard
                  key={c.code}
                  card={c}
                  onClick={(() => {
                    const h5Path = c.actionUrl ? H5_CARD_ROUTES.get(c.actionUrl) : undefined;
                    return h5Path ? () => void navigate(h5Path) : undefined;
                  })()}
                />
              ))}
            </div>
          )}

          {/* 待处理预览 */}
          <Card>
            <div style={{ display: 'flex', alignItems: 'center', padding: '14px 16px 10px' }}>
              <span style={{ fontSize: 14, fontWeight: 600 }}>待处理 ({ov?.todo.total ?? 0})</span>
              <div style={{ flex: 1 }} />
              <span style={{ fontSize: 12, color: 'var(--brand)' }} onClick={go('todos')}>
                查看全部 →
              </span>
            </div>
            {overviewQ.isLoading ? (
              <div style={{ padding: '20px 0', textAlign: 'center', color: 'var(--brand)' }}>
                <DotLoading color="primary" />
              </div>
            ) : ov && ov.todo.items.length > 0 ? (
              ov.todo.items.map((it, i) => (
                <div
                  key={it.label}
                  onClick={() => it.actionUrl && void navigate(`/todos`)}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 10,
                    padding: '12px 16px',
                    borderTop: i === 0 ? 'none' : '1px solid var(--divider, #f0f0f0)',
                  }}
                >
                  <div style={{ flex: 1, fontSize: 13, fontWeight: 500 }}>{it.label}</div>
                  <Tag tone={it.count > 0 ? 'danger' : 'neutral'}>{it.count}</Tag>
                </div>
              ))
            ) : (
              <div
                style={{
                  padding: '24px 0',
                  textAlign: 'center',
                  fontSize: 13,
                  color: 'var(--text-tertiary)',
                }}
              >
                暂无待处理事项
              </div>
            )}
          </Card>
        </div>
      </PullToRefresh>
    </div>
  );
}

function StatCard({
  label,
  value,
  onClick,
}: {
  label: string;
  value: number | string;
  onClick: () => void;
}) {
  return (
    <Card style={{ padding: 14 }}>
      <div onClick={onClick}>
        <div style={{ fontSize: 12, color: 'var(--text-tertiary)', marginBottom: 6 }}>{label}</div>
        <div style={{ fontSize: 26, fontWeight: 700, lineHeight: 1.1 }}>{value}</div>
      </div>
    </Card>
  );
}

/**
 * 卡片 actionUrl（PC 路由路径）→ H5 路由映射：命中才可点击跳转，避免跳进不存在的路由。
 * 由各模块 manifest 的 `h5Routes[].workbenchActionUrl` 声明派生（见 lib/modules.ts），
 * 不再手工维护映射表。
 */
const H5_CARD_ROUTES = new Map(
  moduleRegistry
    .getH5Routes()
    .filter((r) => r.contribution.workbenchActionUrl)
    .map((r) => [r.contribution.workbenchActionUrl as string, r.fullPath] as const),
);

function BusinessCard({
  card,
  onClick,
}: {
  card: TenantWorkbenchBusinessCard;
  onClick?: (() => void) | undefined;
}) {
  const d = card.data;
  return (
    <div onClick={onClick}>
      <Card style={{ padding: '14px 16px' }}>
        <div style={{ display: 'flex', alignItems: 'center', marginBottom: 8 }}>
          <span style={{ fontSize: 13, fontWeight: 600 }}>{card.name}</span>
          {onClick && (
            <>
              <div style={{ flex: 1 }} />
              <span style={{ color: 'var(--text-tertiary)', fontSize: 14 }}>›</span>
            </>
          )}
        </div>
        {!d ? (
          <div style={{ fontSize: 13, color: 'var(--text-tertiary)' }}>暂无数据</div>
        ) : card.display === 'LIST' && d.items ? (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            {d.items.map((it) => (
              <div key={it.label} style={{ display: 'flex', alignItems: 'center', fontSize: 13 }}>
                <span style={{ flex: 1, color: 'var(--text-secondary)' }}>{it.label}</span>
                <span style={{ fontWeight: 600 }}>{it.value}</span>
              </div>
            ))}
          </div>
        ) : (
          <div style={{ display: 'flex', alignItems: 'baseline', gap: 6 }}>
            <span style={{ fontSize: 26, fontWeight: 700 }}>{d.value ?? '—'}</span>
            {d.unit && (
              <span style={{ fontSize: 12, color: 'var(--text-tertiary)' }}>{d.unit}</span>
            )}
            {d.delta && (
              <span
                style={{
                  fontSize: 12,
                  marginLeft: 4,
                  color:
                    d.deltaTone === 'down' ? 'var(--danger, #dc2626)' : 'var(--success, #16a34a)',
                }}
              >
                {d.delta}
              </span>
            )}
          </div>
        )}
      </Card>
    </div>
  );
}

function BellIcon() {
  return (
    <svg
      width="18"
      height="18"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.8}
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path d="M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9" />
      <path d="M13.7 21a2 2 0 0 1-3.4 0" />
    </svg>
  );
}
