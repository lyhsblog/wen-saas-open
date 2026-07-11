import { useSearchParams } from 'react-router';
import { OpsTenantListPane } from './OpsTenantListPane';
import { OpsTenantDetailPanel } from './OpsTenantDetailPanel';

export function OpsTenantsPage() {
  const [sp, setSp] = useSearchParams();
  const selectedId = sp.get('selected');
  const tab = sp.get('tab') ?? 'basic';

  const setSelected = (id: string) => {
    const next = new URLSearchParams(sp);
    next.set('selected', id);
    if (!next.get('tab')) next.set('tab', 'basic');
    setSp(next, { replace: true });
  };

  const clearSelected = () => {
    const next = new URLSearchParams(sp);
    next.delete('selected');
    next.delete('tab');
    setSp(next, { replace: true });
  };

  const setTab = (t: string) => {
    const next = new URLSearchParams(sp);
    next.set('tab', t);
    setSp(next, { replace: true });
  };

  return (
    <div style={{ display: 'flex', height: '100%', overflow: 'hidden' }}>
      <OpsTenantListPane selectedId={selectedId} onSelect={setSelected} compact={!!selectedId} />
      {selectedId && (
        <OpsTenantDetailPanel
          tenantId={selectedId}
          tab={tab}
          onTabChange={setTab}
          onClose={clearSelected}
        />
      )}
    </div>
  );
}
