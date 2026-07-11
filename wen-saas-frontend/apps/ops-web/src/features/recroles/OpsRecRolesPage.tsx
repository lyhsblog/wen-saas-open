import { useSearchParams } from 'react-router';
import { OpsRecRoleListPane } from './OpsRecRoleListPane';
import { OpsRecRoleDetailPanel } from './OpsRecRoleDetailPanel';

export function OpsRecRolesPage() {
  const [sp, setSp] = useSearchParams();
  const raw = sp.get('selected');
  // id 是雪花 Long（字符串），不能转 number，否则超出 JS 安全整数会丢精度。
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
      <OpsRecRoleListPane
        selectedId={selectedId}
        onSelect={setSelected}
        compact={selectedId !== null}
      />
      {selectedId !== null && (
        <OpsRecRoleDetailPanel
          id={selectedId}
          onClose={clearSelected}
          onDeleted={clearSelected}
          onSelect={setSelected}
        />
      )}
    </div>
  );
}
