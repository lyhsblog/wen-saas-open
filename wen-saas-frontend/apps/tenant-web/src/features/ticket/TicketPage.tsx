import { useState } from 'react';
import { useSearchParams } from 'react-router';
import { Drawer } from 'antd';
import { useIsNarrow } from '@wen-saas/shared-ui';
import { TicketListPane } from './TicketListPane';
import { TicketDetailPanel } from './TicketDetailPanel';
import { SubmitTicketModal } from './SubmitTicketModal';

/**
 * 租户后台 · 我的工单。主从布局：左侧工单列表（提交入口 + 状态筛选，
 * 有 base.ticket.view_all 权限时可切「全部工单」）+ 右侧详情（沟通记录 / 回复 / 确认已解决 /
 * 重新打开 / 附件）。选中态用 URL search param `selected`（雪花 Long 字符串，不转 number）。
 * 宽屏（≥1100px）选中后列表 compact 让位给 inline 详情；窄屏列表恒全宽、详情走 Drawer 覆盖。
 */
export function TicketPage() {
  const [sp, setSp] = useSearchParams();
  const [submitOpen, setSubmitOpen] = useState(false);
  const narrow = useIsNarrow();
  const raw = sp.get('selected');
  const selectedId = raw != null && /^\d+$/.test(raw) ? raw : null;

  const setSelected = (id: string) => {
    const next = new URLSearchParams(sp);
    next.set('selected', id);
    setSp(next, { replace: true });
  };

  const clearSelected = () => {
    const next = new URLSearchParams(sp);
    next.delete('selected');
    setSp(next, { replace: true });
  };

  return (
    <div style={{ display: 'flex', height: '100%', overflow: 'hidden' }}>
      <TicketListPane
        selectedId={selectedId}
        onSelect={setSelected}
        onSubmit={() => setSubmitOpen(true)}
        compact={!narrow && selectedId !== null}
      />
      {selectedId !== null &&
        (narrow ? (
          <Drawer
            open
            width="min(480px, 100vw)"
            onClose={clearSelected}
            closable={false}
            destroyOnHidden
            styles={{ body: { padding: 0, overflow: 'hidden' } }}
          >
            <TicketDetailPanel ticketId={selectedId} onClose={clearSelected} fullWidth />
          </Drawer>
        ) : (
          <TicketDetailPanel ticketId={selectedId} onClose={clearSelected} />
        ))}
      <SubmitTicketModal
        open={submitOpen}
        onCancel={() => setSubmitOpen(false)}
        onSubmitted={(created) => {
          setSubmitOpen(false);
          setSelected(created.summary.id);
        }}
      />
    </div>
  );
}
