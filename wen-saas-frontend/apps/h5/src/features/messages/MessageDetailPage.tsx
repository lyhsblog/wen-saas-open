import { useEffect, useRef } from 'react';
import { Button, DotLoading, ErrorBlock, NavBar } from 'antd-mobile';
import { useNavigate, useParams } from 'react-router';
import { Tag } from '../../components/ui';
import { formatDateTime } from '../../lib/format';
import { useMarkRead, useMessageDetail } from './messageQueries';

const CAT_LABEL: Record<string, string> = { SYSTEM: '系统', BUSINESS: '业务', TODO: '待办' };

export function MessageDetailPage() {
  const navigate = useNavigate();
  const { messageId = '' } = useParams<{ messageId: string }>();
  const detailQ = useMessageDetail(messageId);
  const markRead = useMarkRead();
  const markedRef = useRef(false);

  // 打开即标记已读（一次）
  useEffect(() => {
    if (messageId && !markedRef.current) {
      markedRef.current = true;
      markRead.mutate(messageId);
    }
  }, [messageId, markRead]);

  // 从列表进来时走历史返回（保留列表页 URL 上的 tab 筛选）；直链进入无历史则回列表
  const back = () => {
    if (((window.history.state as { idx?: number } | null)?.idx ?? 0) > 0) {
      void navigate(-1);
    } else {
      void navigate(`/messages`);
    }
  };
  const d = detailQ.data;

  return (
    <div style={{ minHeight: '100dvh', background: 'var(--bg)' }}>
      <NavBar onBack={back} style={{ background: 'var(--bg-elev)' }}>
        消息详情
      </NavBar>
      {detailQ.isError ? (
        <div style={{ padding: '64px 24px', textAlign: 'center' }}>
          <ErrorBlock status="default" title="加载失败" description="消息详情获取失败，请检查网络后重试。" />
          <Button color="primary" style={{ marginTop: 16 }} onClick={() => void detailQ.refetch()}>
            重试
          </Button>
        </div>
      ) : detailQ.isLoading || !d ? (
        <div style={{ padding: '64px 0', textAlign: 'center', color: 'var(--brand)' }}>
          <DotLoading color="primary" />
        </div>
      ) : (
        <div style={{ padding: 16 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 10 }}>
            {CAT_LABEL[d.category] && <Tag tone="neutral">{CAT_LABEL[d.category]}</Tag>}
            {d.priority === 'URGENT' && <Tag tone="danger">紧急</Tag>}
          </div>
          <div style={{ fontSize: 18, fontWeight: 600, lineHeight: 1.5 }}>{d.title}</div>
          <div style={{ fontSize: 12, color: 'var(--text-tertiary)', margin: '8px 0 16px' }}>
            {formatDateTime(d.createdAt)}
          </div>
          <div
            style={{ fontSize: 15, lineHeight: 1.8, whiteSpace: 'pre-wrap', color: 'var(--text)' }}
          >
            {d.body ?? '（无正文）'}
          </div>
        </div>
      )}
    </div>
  );
}
