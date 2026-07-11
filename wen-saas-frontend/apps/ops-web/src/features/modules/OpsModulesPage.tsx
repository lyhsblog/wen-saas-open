import { useSearchParams } from 'react-router';
import { OpsModuleListPane } from './OpsModuleListPane';
import { OpsModuleDetailPanel } from './OpsModuleDetailPanel';

/**
 * 运营后台「业务模块」管理页（只读内省，master-detail 布局）。
 *
 * 左侧按状态分组的模块清单 + 右侧详情（基本信息 / 贡献能力 / 关联套餐）。
 * 选中态存 URL `?selected=`，支持书签 / 刷新恢复（对齐套餐管理）。
 */
export function OpsModulesPage() {
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
      <OpsModuleListPane
        selectedCode={selectedCode}
        onSelect={setSelected}
        compact={!!selectedCode}
      />
      {selectedCode && <OpsModuleDetailPanel code={selectedCode} onClose={clearSelected} />}
    </div>
  );
}
