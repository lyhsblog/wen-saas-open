import { useSearchParams } from 'react-router';
import { OpsTicketListPane } from './OpsTicketListPane';
import { OpsTicketDetailPanel } from './OpsTicketDetailPanel';

/**
 * 运营后台 · 工单管理。主从布局：左侧工单列表（多条件筛选 + SLA 态 +
 * 仅我的）+ 右侧详情面板（认领 / 转单 / 改优先级 / 回复 / 解决 / 关闭 / 强制重启 / 附件）。
 * 选中态用 URL search param `selected`（雪花 Long 字符串，不转 number）持久化，可刷新可分享。
 */
export function OpsTicketPage() {
  const [sp, setSp] = useSearchParams();
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
      <OpsTicketListPane
        selectedId={selectedId}
        onSelect={setSelected}
        compact={selectedId !== null}
      />
      {selectedId !== null && (
        <OpsTicketDetailPanel ticketId={selectedId} onClose={clearSelected} />
      )}
    </div>
  );
}
