import { type ReactNode } from 'react';
import { Outlet, useLocation, useNavigate } from 'react-router';
import { TabBar } from 'antd-mobile';
import { WorkspaceIcon, TodoIcon, MessageIcon, ProfileIcon } from './icons';
import { useUnreadSummary } from '../features/messages/messageQueries';

type TabDef = { key: string; title: string; icon: (active: boolean) => ReactNode };

const TABS: TabDef[] = [
  { key: 'workspace', title: '工作台', icon: () => <WorkspaceIcon /> },
  { key: 'todos', title: '待办', icon: () => <TodoIcon /> },
  { key: 'messages', title: '消息', icon: () => <MessageIcon /> },
  { key: 'profile', title: '我', icon: () => <ProfileIcon /> },
];

/**
 * 底部 4-tab 主壳。承载 workspace / todos / messages / profile 四个主页面。
 * 通讯录 / 文件中心是从「我」进入的二级页，不在 TabBar 内。
 */
export function AppLayout() {
  const navigate = useNavigate();
  const location = useLocation();
  const unreadTotal = useUnreadSummary().data?.total ?? 0;

  // 当前激活 tab：路径首段（/{tab}，租户已在子域名）
  const seg = location.pathname.split('/').filter(Boolean)[0] ?? 'workspace';
  const active = TABS.some((t) => t.key === seg) ? seg : 'workspace';

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100dvh' }}>
      <div style={{ flex: 1, overflowY: 'auto', WebkitOverflowScrolling: 'touch' }}>
        <Outlet />
      </div>
      {/* nav landmark：antd-mobile TabBar 渲染为纯 div，外层包 <nav> 让辅助技术可识别为主导航（H5-02） */}
      <nav
        aria-label="底部主导航"
        style={{
          background: 'var(--bg-elev)',
          borderTop: '1px solid var(--border, #ebedf0)',
          paddingBottom: 'env(safe-area-inset-bottom)',
        }}
      >
        <TabBar activeKey={active} onChange={(key) => void navigate(`/${key}`)}>
          {TABS.map((t) => (
            <TabBar.Item
              key={t.key}
              icon={t.icon}
              title={t.title}
              aria-label={
                t.key === 'messages' && unreadTotal > 0
                  ? `${t.title}，${unreadTotal} 条未读`
                  : t.title
              }
              badge={
                t.key === 'messages' && unreadTotal > 0
                  ? unreadTotal > 99
                    ? '99+'
                    : unreadTotal
                  : undefined
              }
            />
          ))}
        </TabBar>
      </nav>
    </div>
  );
}
