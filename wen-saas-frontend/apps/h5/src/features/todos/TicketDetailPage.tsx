import { useEffect, useRef, useState } from 'react';
import { Button, DotLoading, ErrorBlock, NavBar, TextArea, Toast } from 'antd-mobile';
import { useNavigate, useParams } from 'react-router';
import { Tag } from '../../components/ui';
import { formatDateTime } from '../../lib/format';
import {
  useConfirmTicket,
  useReopenTicket,
  useReplyTicket,
  useTicketDetail,
} from './ticketQueries';
import { PRIORITY_LABEL, PRIORITY_TONE, STATUS_LABEL, STATUS_TONE, TYPE_LABEL } from './ticketMeta';

export function TicketDetailPage() {
  const navigate = useNavigate();
  const { ticketId = '' } = useParams<{ ticketId: string }>();
  const detailQ = useTicketDetail(ticketId);
  const reply = useReplyTicket(ticketId);
  const confirm = useConfirmTicket(ticketId);
  const reopen = useReopenTicket(ticketId);
  const [draft, setDraft] = useState('');

  // 从列表进来时走历史返回（保留列表页 URL 上的 tab 筛选）；直链进入无历史则回列表
  const back = () => {
    if (((window.history.state as { idx?: number } | null)?.idx ?? 0) > 0) {
      void navigate(-1);
    } else {
      void navigate(`/todos`);
    }
  };
  const d = detailQ.data;
  const s = d?.summary;

  // 对话流加载/新增后自动滚到最新一条
  const conversationEndRef = useRef<HTMLDivElement>(null);
  const messageCount = d?.messages.length ?? 0;
  useEffect(() => {
    if (messageCount > 0) conversationEndRef.current?.scrollIntoView({ block: 'end' });
  }, [messageCount]);

  const handleReply = async () => {
    const content = draft.trim();
    if (!content) return;
    try {
      await reply.mutateAsync(content);
      setDraft('');
    } catch {
      Toast.show({ content: '发送失败，请重试' });
    }
  };

  const handleConfirm = async () => {
    try {
      await confirm.mutateAsync();
      Toast.show({ icon: 'success', content: '已确认解决' });
    } catch {
      Toast.show({ content: '操作失败' });
    }
  };

  const handleReopen = async () => {
    try {
      await reopen.mutateAsync();
      Toast.show({ icon: 'success', content: '已重启工单' });
    } catch {
      Toast.show({ content: '操作失败' });
    }
  };

  return (
    <div
      style={{
        minHeight: '100dvh',
        display: 'flex',
        flexDirection: 'column',
        background: 'var(--bg)',
      }}
    >
      <NavBar onBack={back} style={{ background: 'var(--bg-elev)' }}>
        工单详情
      </NavBar>

      {detailQ.isError ? (
        <div style={{ flex: 1, padding: '64px 24px', textAlign: 'center' }}>
          <ErrorBlock status="default" title="加载失败" description="工单详情获取失败，请检查网络后重试。" />
          <Button color="primary" style={{ marginTop: 16 }} onClick={() => void detailQ.refetch()}>
            重试
          </Button>
        </div>
      ) : detailQ.isLoading || !d || !s ? (
        <div
          style={{
            flex: 1,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            color: 'var(--brand)',
          }}
        >
          <DotLoading color="primary" />
        </div>
      ) : (
        <>
          <div style={{ flex: 1, overflowY: 'auto', padding: 12 }}>
            {/* 头部摘要 */}
            <div
              style={{
                background: 'var(--bg-elev)',
                borderRadius: 12,
                padding: 16,
                marginBottom: 12,
              }}
            >
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 6,
                  flexWrap: 'wrap',
                  marginBottom: 8,
                }}
              >
                <Tag tone={PRIORITY_TONE[s.priority]}>{PRIORITY_LABEL[s.priority]}</Tag>
                <Tag tone="brand">{TYPE_LABEL[s.type]}</Tag>
                <Tag tone={STATUS_TONE[s.status]}>{STATUS_LABEL[s.status]}</Tag>
              </div>
              <div style={{ fontSize: 16, fontWeight: 600, lineHeight: 1.5 }}>{s.title}</div>
              <div style={{ fontSize: 11, color: 'var(--text-tertiary)', marginTop: 6 }}>
                {s.ticketNo} · 创建于 {formatDateTime(s.createdAt)}
              </div>
            </div>

            {/* 对话流 */}
            <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              {d.messages.map((m) => {
                const mine = m.authorSide === 'TENANT';
                return (
                  <div
                    key={m.id}
                    style={{
                      display: 'flex',
                      flexDirection: 'column',
                      alignItems: mine ? 'flex-end' : 'flex-start',
                    }}
                  >
                    <div
                      style={{ fontSize: 11, color: 'var(--text-tertiary)', margin: '0 4px 4px' }}
                    >
                      {m.authorName ?? (mine ? '我' : '客服')} · {formatDateTime(m.createdAt)}
                    </div>
                    <div
                      style={{
                        maxWidth: '78%',
                        padding: '10px 12px',
                        borderRadius: 12,
                        fontSize: 14,
                        lineHeight: 1.6,
                        whiteSpace: 'pre-wrap',
                        background: mine ? 'var(--brand)' : 'var(--bg-elev)',
                        color: mine ? '#fff' : 'var(--text)',
                      }}
                    >
                      {m.content}
                    </div>
                  </div>
                );
              })}
              <div ref={conversationEndRef} />
            </div>
          </div>

          {/* 底部操作：已解决可确认/重启；否则回复 */}
          <div
            style={{
              borderTop: '1px solid var(--divider, #f0f0f0)',
              background: 'var(--bg-elev)',
              padding: '10px 12px',
              paddingBottom: 'calc(10px + env(safe-area-inset-bottom))',
            }}
          >
            {s.status === 'RESOLVED' ? (
              <div style={{ display: 'flex', gap: 10 }}>
                <Button
                  block
                  fill="outline"
                  onClick={() => void handleReopen()}
                  loading={reopen.isPending}
                >
                  重新打开
                </Button>
                <Button
                  block
                  color="primary"
                  onClick={() => void handleConfirm()}
                  loading={confirm.isPending}
                >
                  确认已解决
                </Button>
              </div>
            ) : s.status === 'CLOSED' ? (
              <div
                style={{
                  textAlign: 'center',
                  fontSize: 13,
                  color: 'var(--text-tertiary)',
                  padding: '6px 0',
                }}
              >
                工单已关闭
              </div>
            ) : (
              <div style={{ display: 'flex', alignItems: 'flex-end', gap: 8 }}>
                <div style={{ flex: 1 }}>
                  <TextArea
                    value={draft}
                    onChange={setDraft}
                    placeholder="输入回复…"
                    autoSize={{ minRows: 1, maxRows: 4 }}
                  />
                </div>
                <Button
                  color="primary"
                  onClick={() => void handleReply()}
                  loading={reply.isPending}
                  disabled={!draft.trim()}
                >
                  发送
                </Button>
              </div>
            )}
          </div>
        </>
      )}
    </div>
  );
}
