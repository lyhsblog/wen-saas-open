import { useRef, useState } from 'react';
import { App, Button, Empty, Input, Popconfirm, Spin, Tooltip } from 'antd';
import { StatusPill } from '@wen-saas/shared-ui';
import { CloseOutlined, PaperClipOutlined, UploadOutlined } from '@ant-design/icons';
import type {
  TicketAttachmentView,
  TicketDetailView,
  TicketMessageView,
} from '@wen-saas/api-client';
import { ticketApi } from './ticketApi';
import {
  formatDateTime,
  formatSize,
  TYPE_LABEL,
  ticketPriorityBadge,
  ticketSlaBadge,
  ticketStatusBadge,
} from './utils/ticketMeta';
import {
  useConfirmTicket,
  useReopenTicket,
  useReplyTicket,
  useTicketDetail,
  useUploadTicketAttachment,
} from './ticketQueries';

type Props = {
  ticketId: string;
  onClose: () => void;
  /** 窄屏放进 Drawer 时铺满容器：宽高 100%、去掉 inline 形态的 440 定宽与左分隔线。 */
  fullWidth?: boolean;
};

function AttachmentChips({
  ticketId,
  attachments,
}: {
  ticketId: string;
  attachments: TicketAttachmentView[];
}) {
  const { message } = App.useApp();
  if (attachments.length === 0) return null;
  const open = async (att: TicketAttachmentView) => {
    try {
      const url = await ticketApi.attachmentUrl(ticketId, att.id);
      window.open(url, '_blank', 'noopener');
    } catch {
      void message.error('获取下载地址失败');
    }
  };
  return (
    <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, marginTop: 6 }}>
      {attachments.map((a) => (
        <button
          key={a.id}
          type="button"
          onClick={() => void open(a)}
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: 4,
            padding: '2px 8px',
            fontSize: 12,
            borderRadius: 'var(--r-sm)',
            border: '1px solid var(--border)',
            background: 'var(--bg-muted)',
            color: 'var(--text-secondary)',
            cursor: 'pointer',
            maxWidth: 220,
          }}
          title={a.fileName}
        >
          <PaperClipOutlined />
          <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
            {a.fileName}
          </span>
          <span style={{ color: 'var(--text-tertiary)' }}>{formatSize(a.sizeBytes)}</span>
        </button>
      ))}
    </div>
  );
}

function MessageBubble({ ticketId, m }: { ticketId: string; m: TicketMessageView }) {
  // 当前租户成员视角：自己（TENANT）靠右，客服（OPS）靠左。
  const self = m.authorSide === 'TENANT';
  return (
    <div style={{ display: 'flex', justifyContent: self ? 'flex-end' : 'flex-start' }}>
      <div style={{ maxWidth: '82%' }}>
        <div
          style={{
            fontSize: 12,
            color: 'var(--text-tertiary)',
            marginBottom: 4,
            textAlign: self ? 'right' : 'left',
          }}
        >
          {m.authorName ?? (self ? '我' : '客服')} · {formatDateTime(m.createdAt)}
        </div>
        <div
          style={{
            padding: '8px 12px',
            borderRadius: 'var(--r-md)',
            fontSize: 13,
            lineHeight: 1.6,
            whiteSpace: 'pre-wrap',
            background: self ? 'var(--brand-bg)' : 'var(--bg-muted)',
            border: '1px solid var(--border)',
          }}
        >
          {m.content}
          <AttachmentChips ticketId={ticketId} attachments={m.attachments} />
        </div>
      </div>
    </div>
  );
}

export function TicketDetailPanel({ ticketId, onClose, fullWidth = false }: Props) {
  const { message } = App.useApp();
  const { data, isLoading } = useTicketDetail(ticketId);
  const [reply, setReply] = useState('');
  const fileRef = useRef<HTMLInputElement>(null);

  const replyM = useReplyTicket(ticketId);
  const confirmM = useConfirmTicket(ticketId);
  const reopenM = useReopenTicket(ticketId);
  const upload = useUploadTicketAttachment(ticketId);

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
      <div style={{ flex: 1, minWidth: 0 }}>
        <div
          style={{ fontWeight: 600, fontSize: 15, overflow: 'hidden', textOverflow: 'ellipsis' }}
        >
          {data?.summary.title ?? '工单详情'}
        </div>
        {data && (
          <div
            style={{
              fontSize: 12,
              color: 'var(--text-tertiary)',
              fontFamily: 'var(--font-mono, monospace)',
            }}
          >
            {data.summary.ticketNo}
          </div>
        )}
      </div>
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
    body = renderBody(data);
  }

  function renderBody(d: TicketDetailView) {
    const s = d.summary;
    const showSla = s.status === 'PENDING' || s.status === 'PROCESSING';
    const meta: { label: string; value: React.ReactNode }[] = [
      { label: '受理客服', value: s.assigneeName ?? '待分配' },
      { label: '类型', value: TYPE_LABEL[s.type] },
      { label: '提交时间', value: formatDateTime(s.createdAt) },
    ];
    if (d.resolvedAt) meta.push({ label: '解决时间', value: formatDateTime(d.resolvedAt) });
    if (d.closedAt) meta.push({ label: '关闭时间', value: formatDateTime(d.closedAt) });
    if (d.closeReason) meta.push({ label: '关闭原因', value: d.closeReason });

    return (
      <div style={{ padding: 20, display: 'flex', flexDirection: 'column', gap: 20 }}>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
          <StatusPill badge={ticketStatusBadge(s.status)} />
          <StatusPill badge={ticketPriorityBadge(s.priority)} />
          {showSla && <StatusPill badge={ticketSlaBadge(s.slaState)} />}
        </div>

        <section>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            {meta.map((row) => (
              <div
                key={row.label}
                style={{ display: 'flex', fontSize: 13, justifyContent: 'space-between', gap: 12 }}
              >
                <span style={{ color: 'var(--text-secondary)', flexShrink: 0 }}>{row.label}</span>
                <span style={{ textAlign: 'right', wordBreak: 'break-all' }}>{row.value}</span>
              </div>
            ))}
          </div>
        </section>

        {d.attachments.length > 0 && (
          <section>
            <div style={{ fontSize: 13, fontWeight: 600, marginBottom: 8 }}>工单附件</div>
            <AttachmentChips ticketId={ticketId} attachments={d.attachments} />
          </section>
        )}

        <section>
          <div style={{ fontSize: 13, fontWeight: 600, marginBottom: 12 }}>
            沟通记录（{d.messages.length}）
          </div>
          {d.messages.length === 0 ? (
            <Empty description="暂无留言" image={Empty.PRESENTED_IMAGE_SIMPLE} />
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
              {d.messages.map((m) => (
                <MessageBubble key={m.id} ticketId={ticketId} m={m} />
              ))}
            </div>
          )}
        </section>
      </div>
    );
  }

  const status = data?.summary.status;
  const canReply = status === 'PENDING' || status === 'PROCESSING';

  const actions = data && (
    <div
      style={{
        borderTop: '1px solid var(--divider)',
        padding: '12px 20px',
        display: 'flex',
        flexDirection: 'column',
        gap: 10,
        background: 'var(--bg-elev)',
      }}
    >
      {canReply && (
        <>
          <Input.TextArea
            value={reply}
            onChange={(e) => setReply(e.target.value)}
            placeholder="补充说明 / 回复客服…"
            autoSize={{ minRows: 2, maxRows: 5 }}
          />
          <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
            <input
              ref={fileRef}
              type="file"
              style={{ display: 'none' }}
              onChange={(e) => {
                const f = e.target.files?.[0];
                if (f) {
                  if (f.size > 10 * 1024 * 1024) {
                    message.error(
                      `文件大小 ${(f.size / 1024 / 1024).toFixed(1)} MB，超出单个附件 10 MB 上限`,
                    );
                  } else {
                    upload.mutate(f);
                  }
                }
                e.target.value = '';
              }}
            />
            <Tooltip title="上传附件（单个 ≤ 10MB）">
              <Button
                icon={<UploadOutlined />}
                aria-label="上传附件"
                loading={upload.isPending}
                onClick={() => fileRef.current?.click()}
              />
            </Tooltip>
            <div style={{ flex: 1 }} />
            <Button
              type="primary"
              loading={replyM.isPending}
              disabled={!reply.trim()}
              onClick={() => replyM.mutate(reply.trim(), { onSuccess: () => setReply('') })}
            >
              发送
            </Button>
          </div>
        </>
      )}

      {status === 'RESOLVED' && (
        <div style={{ display: 'flex', gap: 8 }}>
          <Popconfirm
            title="确认问题已解决？"
            description="确认后工单将关闭。"
            okText="确认解决"
            cancelText="取消"
            onConfirm={() => confirmM.mutate()}
          >
            <Button type="primary" loading={confirmM.isPending}>
              确认已解决
            </Button>
          </Popconfirm>
          <Button loading={reopenM.isPending} onClick={() => reopenM.mutate()}>
            问题仍在，重新打开
          </Button>
        </div>
      )}

      {status === 'CLOSED' && (
        <Popconfirm
          title="重新打开工单？"
          description="关闭 7 天内可重启，客服将重新跟进。"
          okText="重新打开"
          cancelText="取消"
          onConfirm={() => reopenM.mutate()}
        >
          <Button loading={reopenM.isPending}>重新打开</Button>
        </Popconfirm>
      )}
    </div>
  );

  return (
    <div
      style={{
        flexShrink: 0,
        display: 'flex',
        flexDirection: 'column',
        background: 'var(--bg-elev)',
        overflow: 'hidden',
        ...(fullWidth
          ? { width: '100%', height: '100%' }
          : { width: 440, borderLeft: '1px solid var(--border)' }),
      }}
    >
      {header}
      <div style={{ flex: 1, overflowY: 'auto' }}>{body}</div>
      {actions}
    </div>
  );
}
