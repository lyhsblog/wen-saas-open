import { useRef, useState } from 'react';
import { Alert, App, Button, Empty, Input, Modal, Popconfirm, Select, Spin, Tooltip } from 'antd';
import { CloseOutlined, PaperClipOutlined, SwapOutlined, UploadOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { opsTicketApi } from '@wen-saas/api-client';
import type {
  TicketAttachmentView,
  TicketDetailView,
  TicketMessageView,
  TicketPriority,
} from '@wen-saas/api-client';
import { http } from '../../lib/http';
import { ErrorState, StatusPill } from '@wen-saas/shared-ui';
import {
  formatDateTime,
  formatSize,
  PRIORITY_META,
  priorityBadge,
  slaBadge,
  statusBadge,
  TYPE_LABEL,
} from './utils/ticketMeta';
import {
  useChangePriority,
  useClaimTicket,
  useCloseTicket,
  useReopenTicket,
  useReplyTicket,
  useResolveTicket,
  useTransferTicket,
  useUploadAttachment,
} from './hooks/useTicketMutations';

type Props = {
  ticketId: string;
  onClose: () => void;
};

const PRIORITY_OPTIONS: { value: TicketPriority; label: string }[] = [
  { value: 'URGENT', label: '紧急（24 小时）' },
  { value: 'HIGH', label: '重要（48 小时）' },
  { value: 'NORMAL', label: '普通（72 小时）' },
  { value: 'LOW', label: '低（7 天）' },
];

function AttachmentChips({
  ticketId,
  attachments,
}: {
  ticketId: string;
  attachments: TicketAttachmentView[];
}) {
  const { message } = App.useApp();
  const openAttachment = async (att: TicketAttachmentView) => {
    try {
      const url = await opsTicketApi(http).attachmentUrl(ticketId, att.id);
      window.open(url, '_blank', 'noopener');
    } catch {
      void message.error('获取下载地址失败');
    }
  };
  if (attachments.length === 0) return null;
  return (
    <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, marginTop: 6 }}>
      {attachments.map((a) => (
        <button
          key={a.id}
          type="button"
          onClick={() => void openAttachment(a)}
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
  const ops = m.authorSide === 'OPS';
  return (
    <div style={{ display: 'flex', justifyContent: ops ? 'flex-end' : 'flex-start' }}>
      <div style={{ maxWidth: '82%' }}>
        <div
          style={{
            fontSize: 12,
            color: 'var(--text-tertiary)',
            marginBottom: 4,
            textAlign: ops ? 'right' : 'left',
          }}
        >
          {m.authorName ?? (ops ? '客服' : '租户')} · {formatDateTime(m.createdAt)}
        </div>
        <div
          style={{
            padding: '8px 12px',
            borderRadius: 'var(--r-md)',
            fontSize: 13,
            lineHeight: 1.6,
            whiteSpace: 'pre-wrap',
            background: ops ? 'var(--brand-bg)' : 'var(--bg-muted)',
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

export function OpsTicketDetailPanel({ ticketId, onClose }: Props) {
  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ['ops', 'ticket', ticketId],
    queryFn: () => opsTicketApi(http).getDetail(ticketId),
  });

  const [reply, setReply] = useState('');
  const [transferOpen, setTransferOpen] = useState(false);
  const [transferTo, setTransferTo] = useState<string | undefined>(undefined);
  const [priorityOpen, setPriorityOpen] = useState(false);
  const [nextPriority, setNextPriority] = useState<TicketPriority | undefined>(undefined);
  const [closeOpen, setCloseOpen] = useState(false);
  const [closeReason, setCloseReason] = useState('');
  const fileRef = useRef<HTMLInputElement>(null);

  const claim = useClaimTicket();
  const replyM = useReplyTicket(ticketId);
  const resolve = useResolveTicket();
  const reopen = useReopenTicket();
  const transfer = useTransferTicket(ticketId);
  const changePriority = useChangePriority(ticketId);
  const closeM = useCloseTicket(ticketId);
  const upload = useUploadAttachment(ticketId);

  const { data: assignees } = useQuery({
    queryKey: ['ops', 'tickets', 'assignees'],
    queryFn: () => opsTicketApi(http).assignees(),
    enabled: transferOpen,
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
  if (isLoading) {
    body = (
      <div style={{ padding: 48, textAlign: 'center' }}>
        <Spin />
      </div>
    );
  } else if (isError || !data) {
    body = (
      <ErrorState
        title="加载工单详情失败"
        description={error instanceof Error ? error.message : undefined}
        primaryAction={{ label: '重试', onClick: () => void refetch() }}
      />
    );
  } else {
    body = renderBody(data);
  }

  function renderBody(d: TicketDetailView) {
    const s = d.summary;
    const meta: { label: string; value: React.ReactNode }[] = [
      { label: '租户', value: s.tenantName ?? '—' },
      { label: '提交人', value: s.submitterName ?? '—' },
      { label: '受理人', value: s.assigneeName ?? '未认领' },
      { label: '类型', value: TYPE_LABEL[s.type] },
      { label: 'SLA 到期', value: formatDateTime(s.slaDueAt) },
      { label: '创建时间', value: formatDateTime(s.createdAt) },
    ];
    if (d.closeReason) meta.push({ label: '关闭原因', value: d.closeReason });

    return (
      <div style={{ padding: 20, display: 'flex', flexDirection: 'column', gap: 20 }}>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
          <StatusPill badge={statusBadge(s.status)} />
          <StatusPill badge={priorityBadge(s.priority)} />
          {(s.status === 'PENDING' || s.status === 'PROCESSING') && (
            <StatusPill badge={slaBadge(s.slaState)} />
          )}
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
  // PROCESSING 直接关闭=跳过「已解决」，关闭原因必填并同步展示给租户；RESOLVED 关闭仍可选。
  const mustReason = status === 'PROCESSING';

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
            placeholder="输入回复内容…（回复会自动认领待处理工单）"
            autoSize={{ minRows: 2, maxRows: 5 }}
          />
          <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
            <input
              ref={fileRef}
              type="file"
              style={{ display: 'none' }}
              onChange={(e) => {
                const f = e.target.files?.[0];
                if (f) upload.mutate(f);
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
              发送回复
            </Button>
          </div>
        </>
      )}

      <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
        {status === 'PENDING' && (
          <Button type="primary" loading={claim.isPending} onClick={() => claim.mutate(ticketId)}>
            认领
          </Button>
        )}
        {status === 'PROCESSING' && (
          <>
            <Button loading={resolve.isPending} onClick={() => resolve.mutate(ticketId)}>
              标记已解决
            </Button>
            <Button icon={<SwapOutlined />} onClick={() => setTransferOpen(true)}>
              转单
            </Button>
            <Button
              onClick={() => {
                setNextPriority(data.summary.priority);
                setPriorityOpen(true);
              }}
            >
              改优先级
            </Button>
            <Button danger onClick={() => setCloseOpen(true)}>
              关闭
            </Button>
          </>
        )}
        {status === 'RESOLVED' && (
          <Button danger onClick={() => setCloseOpen(true)}>
            关闭
          </Button>
        )}
        {status === 'CLOSED' && (
          <Popconfirm
            title="强制重启工单？"
            description="仅超级管理员可强制重启已关闭工单。"
            okText="重启"
            cancelText="取消"
            onConfirm={() => reopen.mutate(ticketId)}
          >
            <Button loading={reopen.isPending}>强制重启</Button>
          </Popconfirm>
        )}
      </div>
    </div>
  );

  return (
    <div
      style={{
        width: 440,
        flexShrink: 0,
        display: 'flex',
        flexDirection: 'column',
        background: 'var(--bg-elev)',
        overflow: 'hidden',
      }}
    >
      {header}
      <div style={{ flex: 1, overflowY: 'auto' }}>{body}</div>
      {actions}

      <Modal
        title="转单"
        open={transferOpen}
        onCancel={() => setTransferOpen(false)}
        okText="转单"
        cancelText="取消"
        okButtonProps={{ disabled: !transferTo, loading: transfer.isPending }}
        onOk={() => {
          if (!transferTo) return;
          transfer.mutate(transferTo, {
            onSuccess: () => {
              setTransferOpen(false);
              setTransferTo(undefined);
            },
          });
        }}
      >
        <Select
          style={{ width: '100%' }}
          placeholder="选择受理人"
          showSearch
          optionFilterProp="label"
          value={transferTo}
          onChange={setTransferTo}
          options={(assignees ?? []).map((a) => ({ value: a.userId, label: a.name ?? a.userId }))}
        />
      </Modal>

      <Modal
        title="调整优先级"
        open={priorityOpen}
        onCancel={() => setPriorityOpen(false)}
        okText="保存"
        cancelText="取消"
        okButtonProps={{ disabled: !nextPriority, loading: changePriority.isPending }}
        onOk={() => {
          if (!nextPriority) return;
          changePriority.mutate(nextPriority, { onSuccess: () => setPriorityOpen(false) });
        }}
      >
        <div style={{ fontSize: 13, color: 'var(--text-secondary)', marginBottom: 10 }}>
          调整后将按新优先级（{nextPriority ? PRIORITY_META[nextPriority].sla : ''}）重算 SLA
          到期时间。
        </div>
        <Select
          style={{ width: '100%' }}
          value={nextPriority}
          onChange={setNextPriority}
          options={PRIORITY_OPTIONS}
        />
      </Modal>

      <Modal
        title="关闭工单"
        open={closeOpen}
        onCancel={() => setCloseOpen(false)}
        okText="关闭工单"
        cancelText="取消"
        okButtonProps={{
          danger: true,
          loading: closeM.isPending,
          disabled: mustReason && !closeReason.trim(),
        }}
        onOk={() =>
          closeM.mutate(closeReason.trim(), {
            onSuccess: () => {
              setCloseOpen(false);
              setCloseReason('');
            },
          })
        }
      >
        {mustReason && (
          <Alert
            type="warning"
            showIcon
            style={{ marginBottom: 12 }}
            message="该工单尚未解决，直接关闭需填写原因（将同步展示给租户）"
          />
        )}
        <Input.TextArea
          value={closeReason}
          onChange={(e) => setCloseReason(e.target.value)}
          placeholder={mustReason ? '必填：说明关闭原因' : '关闭原因（可选）'}
          autoSize={{ minRows: 2, maxRows: 4 }}
        />
      </Modal>
    </div>
  );
}
