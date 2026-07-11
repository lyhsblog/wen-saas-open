import { useSearchParams } from 'react-router';
import { OpsPackageListPane } from './OpsPackageListPane';
import { OpsPackageDetailPanel } from './OpsPackageDetailPanel';

export function OpsPackagesPage() {
  const [sp, setSp] = useSearchParams();
  const selectedCode = sp.get('selected');

  const setSelected = (code: string) => {
    const next = new URLSearchParams(sp);
    next.set('selected', code);
    setSp(next, { replace: true });
  };

  const clearSelected = () => {
    const next = new URLSearchParams(sp);
    next.delete('selected');
    setSp(next, { replace: true });
  };

  return (
    <div style={{ display: 'flex', height: '100%', overflow: 'hidden' }}>
      <OpsPackageListPane
        selectedCode={selectedCode}
        onSelect={setSelected}
        compact={!!selectedCode}
      />
      {selectedCode && (
        <OpsPackageDetailPanel
          code={selectedCode}
          onClose={clearSelected}
          onDeleted={clearSelected}
          onSelect={setSelected}
        />
      )}
    </div>
  );
}
