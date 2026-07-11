import { useSearchParams } from 'react-router';
import { OpsNotificationTemplateListPane } from './OpsNotificationTemplateListPane';
import { OpsNotificationTemplateDetailPanel } from './OpsNotificationTemplateDetailPanel';

export function OpsNotificationTemplatesPage() {
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
      <OpsNotificationTemplateListPane
        selectedCode={selectedCode}
        onSelect={setSelected}
        compact={!!selectedCode}
      />
      {selectedCode && (
        <OpsNotificationTemplateDetailPanel
          code={selectedCode}
          onClose={clearSelected}
          onDeleted={clearSelected}
        />
      )}
    </div>
  );
}
