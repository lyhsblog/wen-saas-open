import {
  Button,
  CapsuleTabs,
  DotLoading,
  Dialog,
  Empty,
  ErrorBlock,
  InfiniteScroll,
  NavBar,
  PullToRefresh,
  Toast,
} from 'antd-mobile';
import { useNavigate, useSearchParams } from 'react-router';
import type { MessageCategory, MessageReadState } from '@wen-saas/api-client';
import { Card, Tag } from '../../components/ui';
import { formatRelative } from '../../lib/format';
import { useInboxInfinite, useMarkAllRead, useUnreadSummary } from './messageQueries';

type TabDef = {
  key: string;
  label: string;
  category?: MessageCategory;
  readState?: MessageReadState;
};

const TABS: TabDef[] = [
  { key: 'all', label: '全部' },
  { key: 'unread', label: '未读', readState: 'UNREAD' },
  { key: 'SYSTEM', label: '系统', category: 'SYSTEM' },
  { key: 'BUSINESS', label: '业务', category: 'BUSINESS' },
  { key: 'TODO', label: '待办', category: 'TODO' },
];

const CAT_LABEL: Record<string, string> = { SYSTEM: '系统', BUSINESS: '业务', TODO: '待办' };

export function MessagesPage() {
  const navigate = useNavigate();
  // tab 放 URL query：进详情返回后保留当前筛选
  const [searchParams, setSearchParams] = useSearchParams();
  const rawTab = searchParams.get('tab') ?? 'all';
  const tab = TABS.some((t) => t.key === rawTab) ? rawTab : 'all';
  const markAll = useMarkAllRead();
  const unreadQ = useUnreadSummary();

  const def = TABS.find((t) => t.key === tab)!;
  const listQ = useInboxInfinite({
    ...(def.category ? { category: def.category } : {}),
    readState: def.readState ?? 'ALL',
  });
  const records = listQ.data?.pages.flatMap((p) => p.records) ?? [];
  const unreadTotal = unreadQ.data?.total ?? 0;
  // 分类页签下「已读」只清本类；「全部/未读」页签下才全清（口径同 tenant-web 消息中心）
  const cat = def.category ?? null;
  const catUnread = cat
    ? (unreadQ.data?.byCategory.find((c) => c.category === cat)?.count ?? 0)
    : unreadTotal;

  const handleMarkAll = async () => {
    if (catUnread === 0) return;
    if (!cat) {
      // 全清不可撤销，加一次轻确认；本类清读范围明确，直接执行
      const ok = await Dialog.confirm({
        title: '将全部消息标为已读？',
        content: '所有分类的未读消息都会被标记，且不可撤销。',
      });
      if (!ok) return;
    }
    try {
      await markAll.mutateAsync(cat);
      Toast.show({ icon: 'success', content: cat ? `已将${def.label}标为已读` : '已全部标为已读' });
    } catch {
      Toast.show({ content: '操作失败' });
    }
  };

  return (
    <div
      style={{
        minHeight: '100%',
        background: 'var(--bg)',
        display: 'flex',
        flexDirection: 'column',
      }}
    >
      <NavBar
        backArrow={false}
        right={
          <span
            style={{ fontSize: 13, color: catUnread ? 'var(--brand)' : 'var(--text-disabled)' }}
            onClick={() => void handleMarkAll()}
          >
            {cat ? '本类已读' : '全部已读'}
          </span>
        }
        style={{ background: 'var(--bg-elev)' }}
      >
        消息{unreadTotal ? ` (${unreadTotal})` : ''}
      </NavBar>

      <CapsuleTabs
        activeKey={tab}
        onChange={(k) => setSearchParams(k === 'all' ? {} : { tab: k }, { replace: true })}
        style={{ background: 'var(--bg-elev)' }}
      >
        {TABS.map((t) => (
          <CapsuleTabs.Tab title={t.label} key={t.key} />
        ))}
      </CapsuleTabs>

      <div style={{ flex: 1, overflow: 'hidden' }}>
        <PullToRefresh
          onRefresh={async () => void (await Promise.all([listQ.refetch(), unreadQ.refetch()]))}
        >
          <div style={{ padding: 12 }}>
            {listQ.isLoading ? (
              <div style={{ padding: '40px 0', textAlign: 'center', color: 'var(--brand)' }}>
                <DotLoading color="primary" />
              </div>
            ) : listQ.isError ? (
              <div style={{ padding: '40px 0', textAlign: 'center' }}>
                <ErrorBlock status="default" title="加载失败" description="请检查网络后重试" />
                <Button
                  color="primary"
                  size="small"
                  style={{ marginTop: 12 }}
                  onClick={() => void listQ.refetch()}
                >
                  重试
                </Button>
              </div>
            ) : records.length === 0 ? (
              <Empty description="暂无消息" style={{ padding: '48px 0' }} />
            ) : (
              <Card style={{ overflow: 'hidden' }}>
                {records.map((m, i) => (
                  <div
                    key={m.id}
                    onClick={() => void navigate(`/messages/${m.id}`)}
                    style={{
                      display: 'flex',
                      alignItems: 'flex-start',
                      gap: 10,
                      padding: '12px 16px',
                      borderTop: i === 0 ? 'none' : '1px solid var(--divider, #f0f0f0)',
                    }}
                  >
                    <div style={{ position: 'relative', marginTop: 4 }}>
                      <span
                        style={{
                          width: 8,
                          height: 8,
                          borderRadius: 4,
                          background: m.read ? 'transparent' : 'var(--danger, #dc2626)',
                          display: 'block',
                        }}
                      />
                    </div>
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div
                        style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 2 }}
                      >
                        <span
                          style={{
                            fontSize: 14,
                            fontWeight: m.read ? 500 : 600,
                            color: m.read ? 'var(--text-secondary)' : 'var(--text)',
                            overflow: 'hidden',
                            textOverflow: 'ellipsis',
                            whiteSpace: 'nowrap',
                          }}
                        >
                          {m.title}
                        </span>
                        {CAT_LABEL[m.category] && <Tag tone="neutral">{CAT_LABEL[m.category]}</Tag>}
                      </div>
                      {m.summary && (
                        <div
                          style={{
                            fontSize: 12,
                            color: 'var(--text-tertiary)',
                            lineHeight: 1.5,
                            overflow: 'hidden',
                            textOverflow: 'ellipsis',
                            whiteSpace: 'nowrap',
                          }}
                        >
                          {m.summary}
                        </div>
                      )}
                      <div style={{ fontSize: 11, color: 'var(--text-tertiary)', marginTop: 4 }}>
                        {formatRelative(m.createdAt)}
                      </div>
                    </div>
                  </div>
                ))}
              </Card>
            )}
            {records.length > 0 && (
              <InfiniteScroll
                loadMore={async () => {
                  await listQ.fetchNextPage();
                }}
                hasMore={listQ.hasNextPage}
              />
            )}
          </div>
        </PullToRefresh>
      </div>
    </div>
  );
}
