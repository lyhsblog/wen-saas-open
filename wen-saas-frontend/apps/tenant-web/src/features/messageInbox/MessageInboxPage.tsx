import { useEffect, useMemo, useState } from 'react';
import {
  CountChip,
  ErrorState,
  formatDateTime,
  formatDateTimeMinute,
  shouldSkipRowNav,
  StatusPill,
  TabChip,
  toneBadge,
  useIsNarrow,
  type SemanticTone,
} from '@wen-saas/shared-ui';
import {
  App,
  Badge,
  Button,
  Card,
  Drawer,
  Empty,
  Input,
  List,
  Pagination,
  Segmented,
  Space,
  Spin,
  Table,
} from 'antd';
import { CheckOutlined, DeleteOutlined, ExportOutlined } from '@ant-design/icons';
import { useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router';
import { describeApiError, type InboxQuery } from '@wen-saas/api-client';
import { tenantPath, useTenantKey } from '../../lib/tenant-path';
import { moduleDisplayName } from '../../lib/moduleNames';
import {
  inboxKeys,
  useDeliveries,
  useDeleteMessage,
  useInboxList,
  useMarkAllRead,
  useMessageDetail,
  useUnreadCount,
} from './messageInboxQueries';

const CATEGORY_LABEL: Record<string, string> = {
  SYSTEM: '系统通知',
  BUSINESS: '业务通知',
  TODO: '待办提醒',
};
// 投递明细：通道 / 状态枚举 → 中文（未知码兜底原值）
const CHANNEL_LABEL: Record<string, string> = {
  STATION: '站内信',
  EMAIL: '邮件',
  SMS: '短信',
  WECHAT: '微信',
  WECOM: '企业微信',
  DINGTALK: '钉钉',
};
const DELIVERY_STATUS_META: Record<string, { label: string; tone: SemanticTone }> = {
  SENT: { label: '已发送', tone: 'success' },
  FAILED: { label: '失败', tone: 'danger' },
  PENDING: { label: '待发送', tone: 'neutral' },
  SKIPPED: { label: '已跳过', tone: 'neutral' },
};

type Tab = 'ALL' | 'UNREAD' | 'SYSTEM' | 'BUSINESS' | 'TODO';

function tabToQuery(tab: Tab): Pick<InboxQuery, 'category' | 'readState'> {
  switch (tab) {
    case 'UNREAD':
      return { readState: 'UNREAD' };
    case 'SYSTEM':
    case 'BUSINESS':
    case 'TODO':
      return { category: tab };
    default:
      return { readState: 'ALL' };
  }
}

export function MessageInboxPage() {
  const { message, modal } = App.useApp();
  const navigate = useNavigate();
  const tenantKey = useTenantKey();
  const qc = useQueryClient();
  // 宽屏（≥1100px）左列表 420 + 右详情并排；窄屏列表全宽、点开消息详情走 Drawer 覆盖
  const narrow = useIsNarrow();

  const [tab, setTab] = useState<Tab>('ALL');
  const [keyword, setKeyword] = useState('');
  const [archived, setArchived] = useState(false);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [page, setPage] = useState(1);

  const query: InboxQuery = { ...tabToQuery(tab), keyword, archived, page, size: 50 };
  const listQuery = useInboxList(query);
  const unreadQuery = useUnreadCount();
  const detailQuery = useMessageDetail(selectedId);
  const deliveriesQuery = useDeliveries(selectedId);
  const markAllRead = useMarkAllRead();
  const deleteMessage = useDeleteMessage();

  // 打开详情会在后端标已读 → 刷新列表 + 未读数
  useEffect(() => {
    if (detailQuery.data) {
      void qc.invalidateQueries({ queryKey: inboxKeys.list(query) });
      void qc.invalidateQueries({ queryKey: inboxKeys.unread() });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [detailQuery.data?.id]);

  const unreadByCat = useMemo(() => {
    const m: Record<string, number> = {};
    for (const c of unreadQuery.data?.byCategory ?? []) m[c.category] = c.count;
    return m;
  }, [unreadQuery.data]);
  const unreadTotal = unreadQuery.data?.total ?? 0;

  const items = listQuery.data?.records ?? [];
  const detail = detailQuery.data;

  // 分类页签下只清本类未读；「全部/未读」页签下全清（不可逆，加一次轻确认）
  const catTab = tab === 'SYSTEM' || tab === 'BUSINESS' || tab === 'TODO' ? tab : null;

  const handleMarkAllRead = async () => {
    const doMark = async () => {
      try {
        await markAllRead.mutateAsync(catTab);
        message.success(catTab ? `已将${CATEGORY_LABEL[catTab]}标为已读` : '已全部标为已读');
      } catch (e) {
        message.error(describeApiError(e, '操作失败'));
      }
    };
    if (catTab) {
      await doMark();
      return;
    }
    modal.confirm({
      title: '将全部消息标为已读？',
      content: '所有分类的未读消息都会被标记，且不可撤销。',
      onOk: doMark,
    });
  };

  const handleDelete = (id: string, title: string) => {
    modal.confirm({
      title: `删除消息「${title}」？`,
      content: '删除后不可恢复。',
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await deleteMessage.mutateAsync(id);
          setSelectedId(null);
          message.success('已删除');
        } catch (e) {
          message.error(describeApiError(e, '删除失败'));
        }
      },
    });
  };

  // 分类页签走 shared-ui TabChip（品牌浅底选中态），badge 传该类未读数（0 / 空不渲染）
  const tabNode = (key: Tab, label: string, badge?: number) => (
    <TabChip
      active={tab === key}
      onClick={() => {
        setTab(key);
        setPage(1);
      }}
      {...(badge ? { badge } : {})}
    >
      {label}
    </TabChip>
  );

  // 详情内容：宽屏渲染在右侧面板、窄屏渲染在 Drawer（选中后才有）
  const detailNode = detailQuery.isError ? (
    <ErrorState
      title="消息详情加载失败"
      description="请稍后重试。"
      primaryAction={{ label: '重试', onClick: () => void detailQuery.refetch() }}
    />
  ) : detailQuery.isLoading || !detail ? (
    <div style={{ textAlign: 'center', marginTop: 80 }}>
      <Spin />
    </div>
  ) : (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8 }}>
        <h2 style={{ margin: 0, flex: 1 }}>{detail.title}</h2>
        {detail.priority === 'IMPORTANT' && <StatusPill badge={toneBadge('danger', '重要')} />}
        <CountChip>{CATEGORY_LABEL[detail.category] ?? detail.category}</CountChip>
        <Button
          danger
          size="small"
          icon={<DeleteOutlined />}
          onClick={() => handleDelete(detail.id, detail.title)}
        >
          删除
        </Button>
      </div>
      <div style={{ fontSize: 12, color: 'var(--text-tertiary)', marginBottom: 16 }}>
        来源：{moduleDisplayName(detail.sourceModule)} · 发送时间：
        {formatDateTime(detail.createdAt)}
      </div>

      {detail.relatedType && detail.relatedId && (
        <Card size="small" style={{ marginBottom: 16 }}>
          <Space>
            <span>
              关联{detail.relatedType}：{detail.relatedId}
            </span>
            {detail.link && (
              <Button
                type="link"
                icon={<ExportOutlined />}
                onClick={() => {
                  void navigate(
                    detail.link!.startsWith('/')
                      ? tenantPath(tenantKey, detail.link!)
                      : detail.link!,
                  );
                }}
              >
                前往处理
              </Button>
            )}
          </Space>
        </Card>
      )}

      <div
        style={{
          whiteSpace: 'pre-wrap',
          lineHeight: 1.7,
          padding: 16,
          background: 'var(--bg-muted)',
          borderRadius: 'var(--r-md)',
          marginBottom: 24,
        }}
      >
        {detail.body ?? '（无正文）'}
      </div>

      <div style={{ fontWeight: 600, marginBottom: 8 }}>投递明细</div>
      <Table
        size="small"
        rowKey={(r) => r.channel}
        loading={deliveriesQuery.isLoading}
        pagination={false}
        dataSource={deliveriesQuery.data ?? []}
        columns={[
          {
            title: '通道',
            dataIndex: 'channel',
            width: 120,
            render: (c: string) => CHANNEL_LABEL[c] ?? c,
          },
          {
            title: '状态',
            dataIndex: 'status',
            width: 120,
            render: (s: string) => (
              <StatusPill
                badge={toneBadge(
                  DELIVERY_STATUS_META[s]?.tone ?? 'neutral',
                  DELIVERY_STATUS_META[s]?.label ?? s,
                )}
              />
            ),
          },
          {
            title: '投递时间',
            dataIndex: 'sentAt',
            render: (v?: string) => formatDateTime(v),
          },
          { title: '失败原因', dataIndex: 'errorReason', render: (v?: string) => v ?? '-' },
        ]}
      />
    </div>
  );

  return (
    <div style={{ display: 'flex', height: '100%' }}>
      {/* 左：列表（窄屏全宽，详情改走 Drawer） */}
      <div
        style={{
          ...(narrow
            ? { flex: 1, minWidth: 0 }
            : { width: 420, borderRight: '1px solid var(--border)' }),
          display: 'flex',
          flexDirection: 'column',
        }}
      >
        <div style={{ padding: 16, borderBottom: '1px solid var(--border)' }}>
          <div style={{ display: 'flex', alignItems: 'center', marginBottom: 12 }}>
            <span style={{ fontWeight: 600, fontSize: 16 }}>消息中心</span>
            {unreadTotal > 0 && <Badge count={unreadTotal} style={{ marginLeft: 8 }} />}
            <div style={{ flex: 1 }} />
            <Button
              size="small"
              type="link"
              icon={<CheckOutlined />}
              onClick={() => {
                void handleMarkAllRead();
              }}
            >
              {catTab ? '本类已读' : '全部已读'}
            </Button>
          </div>
          <Input.Search
            placeholder="搜索标题 / 正文"
            allowClear
            onSearch={(v) => {
              setKeyword(v);
              setPage(1);
            }}
            style={{ marginBottom: 12 }}
          />
          <div role="tablist" style={{ display: 'flex', flexWrap: 'wrap', gap: 4 }}>
            {tabNode('ALL', '全部')}
            {tabNode('UNREAD', '未读', unreadTotal)}
            {tabNode('SYSTEM', '系统', unreadByCat.SYSTEM)}
            {tabNode('BUSINESS', '业务', unreadByCat.BUSINESS)}
            {tabNode('TODO', '待办', unreadByCat.TODO)}
          </div>
          <div style={{ marginTop: 8 }}>
            <Segmented
              size="small"
              value={archived ? 'archived' : 'inbox'}
              onChange={(v) => {
                setArchived(v === 'archived');
                setPage(1);
              }}
              options={[
                { label: '收件箱', value: 'inbox' },
                { label: '归档（90 天前）', value: 'archived' },
              ]}
            />
          </div>
        </div>
        <div style={{ flex: 1, overflow: 'auto' }}>
          <List
            loading={listQuery.isLoading}
            dataSource={items}
            locale={{ emptyText: <Empty description="暂无消息" /> }}
            renderItem={(it) => (
              <List.Item
                onClick={() => {
                  if (shouldSkipRowNav()) return;
                  setSelectedId(it.id);
                }}
                style={{
                  cursor: 'pointer',
                  padding: '12px 16px',
                  background: selectedId === it.id ? 'var(--bg-muted)' : undefined,
                  borderLeft:
                    selectedId === it.id ? '3px solid var(--brand)' : '3px solid transparent',
                }}
              >
                <div style={{ width: '100%' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                    {!it.read && <Badge dot />}
                    <span style={{ fontWeight: it.read ? 400 : 600, flex: 1 }}>{it.title}</span>
                    {it.priority === 'IMPORTANT' && (
                      <StatusPill badge={toneBadge('danger', '重要')} />
                    )}
                    <CountChip>{CATEGORY_LABEL[it.category] ?? it.category}</CountChip>
                  </div>
                  {it.summary && (
                    <div
                      style={{
                        fontSize: 12,
                        color: 'var(--text-tertiary)',
                        marginTop: 4,
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                        whiteSpace: 'nowrap',
                      }}
                    >
                      {it.summary}
                    </div>
                  )}
                  <div style={{ fontSize: 11, color: 'var(--text-tertiary)', marginTop: 4 }}>
                    {moduleDisplayName(it.sourceModule)} · {formatDateTimeMinute(it.createdAt)}
                  </div>
                </div>
              </List.Item>
            )}
          />
        </div>
        <Pagination
          simple
          current={page}
          pageSize={50}
          total={listQuery.data?.total ?? 0}
          onChange={setPage}
          style={{ padding: '8px 16px', textAlign: 'center', borderTop: '1px solid var(--border)' }}
        />
      </div>

      {/* 右：详情（宽屏 inline；窄屏不渲染，改走下方 Drawer） */}
      {!narrow && (
        <div style={{ flex: 1, padding: 24, overflow: 'auto' }}>
          {!selectedId ? (
            <Empty description="选择左侧消息查看详情" style={{ marginTop: 80 }} />
          ) : (
            detailNode
          )}
        </div>
      )}

      {narrow && selectedId && (
        <Drawer
          open
          title="消息详情"
          width="min(560px, 100vw)"
          onClose={() => setSelectedId(null)}
          destroyOnHidden
        >
          {detailNode}
        </Drawer>
      )}
    </div>
  );
}
