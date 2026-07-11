import { useSearchParams } from 'react-router';
import { OpsHealthOverviewCards } from './OpsHealthOverviewCards';
import { OpsHealthListPane } from './OpsHealthListPane';
import { OpsHealthDetailPanel } from './OpsHealthDetailPanel';

/**
 * 运营后台 · 客户健康度。纯信息看板：
 * 总览三档卡片 + 租户健康度列表（分档筛选 + 搜索）+ 单租户详情（实时重算）。
 */
export function OpsHealthPage() {
  const [sp, setSp] = useSearchParams();
  const raw = sp.get('selected');
  // tenantId 是雪花 Long（字符串），不能转 number，否则丢精度。
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
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%', overflow: 'hidden' }}>
      <div style={{ padding: '20px 24px 0' }}>
        <OpsHealthOverviewCards />
      </div>
      <div style={{ flex: 1, display: 'flex', overflow: 'hidden', marginTop: 16 }}>
        <OpsHealthListPane
          selectedId={selectedId}
          onSelect={setSelected}
          compact={selectedId !== null}
        />
        {selectedId !== null && (
          <OpsHealthDetailPanel tenantId={selectedId} onClose={clearSelected} />
        )}
      </div>
    </div>
  );
}
