import { useState } from 'react';
import { App } from 'antd';
import { useNavigate, useSearchParams } from 'react-router';
import { useBasicQuery, useTeamsRolesQuery } from './profileQueries';
import { authService } from '../../lib/http';
import { SideCard } from './components/SideCard';
import { BasicSection } from './sections/BasicSection';
import { TeamsRolesSection } from './sections/TeamsRolesSection';
import { SecuritySection } from './sections/SecuritySection';
import { SsoSection } from './sections/SsoSection';
import { DevicesSection } from './sections/DevicesSection';
import { NotifySection } from './sections/NotifySection';
import { SECTIONS, type SectionId } from './constants';

/** 点击左侧 tab 只渲染当前 tab 内容；各 tab 数据由对应 section 自取，切换时实时刷新。 */
function ActiveSection({
  id,
  onDirtyChange,
}: {
  id: SectionId;
  onDirtyChange: (dirty: boolean) => void;
}) {
  switch (id) {
    case 'basic':
      return <BasicSection />;
    case 'team':
      return <TeamsRolesSection />;
    case 'security':
      return <SecuritySection />;
    case 'sso':
      return <SsoSection />;
    case 'devices':
      return <DevicesSection />;
    case 'notify':
      return <NotifySection onDirtyChange={onDirtyChange} />;
    default:
      return null;
  }
}

export function OpsProfilePage() {
  const navigate = useNavigate();
  const { modal } = App.useApp();
  // 左侧卡片常驻展示头像 / 姓名 / 角色，所以 basic + teamsRoles 始终拉取。
  const basic = useBasicQuery();
  const team = useTeamsRolesQuery();
  // 支持 ?section= 深链（工作台「去开启二步验证」→ 安全设置）。
  const [params] = useSearchParams();
  const [activeId, setActiveId] = useState<SectionId>(() => {
    const s = params.get('section');
    return SECTIONS.some((n) => n.id === s) ? (s as SectionId) : 'basic';
  });
  // 当前 section 的未保存改动（由 section 上抛），切换分区前 modal.confirm 拦截。
  const [sectionDirty, setSectionDirty] = useState(false);

  const handleSelect = (id: SectionId) => {
    if (id === activeId) return;
    if (sectionDirty) {
      modal.confirm({
        title: '放弃未保存的更改？',
        content: '当前分区有未保存的修改，切换后将丢失。',
        okText: '放弃修改',
        okButtonProps: { danger: true },
        cancelText: '继续编辑',
        onOk: () => {
          setSectionDirty(false);
          setActiveId(id);
        },
      });
      return;
    }
    setActiveId(id);
  };

  const handleLogout = () => {
    void (async () => {
      await authService.logout();
      await navigate('/login', { replace: true });
    })();
  };

  return (
    <div
      style={{
        padding: 24,
        height: '100%',
        overflowY: 'auto',
        display: 'flex',
        gap: 24,
        alignItems: 'flex-start',
      }}
    >
      <SideCard
        basic={basic.data}
        roles={team.data?.roles}
        activeId={activeId}
        onSelect={handleSelect}
        onLogout={handleLogout}
      />
      <div style={{ flex: 1, minWidth: 0 }}>
        {/* key 让切 tab 时整段 remount，触发该 tab query 重新拉取，保证信息实时 */}
        <ActiveSection key={activeId} id={activeId} onDirtyChange={setSectionDirty} />
      </div>
    </div>
  );
}
