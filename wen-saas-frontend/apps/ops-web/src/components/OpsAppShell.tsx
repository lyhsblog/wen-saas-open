import { Fragment, useMemo, type ReactNode } from 'react';
import { RightOutlined } from '@ant-design/icons';
import { useLocation, useNavigate } from 'react-router';
import { useAuthStore } from '@wen-saas/auth';
import { WatermarkLayer, useCapabilitiesStore, useMenus } from '@wen-saas/permissions';
import { AppShell, flattenNavTree, navLabelOf, selectNavKey } from '@wen-saas/shared-ui';
import { authService } from '../lib/http';
import { useBasicQuery } from '../features/profile/profileQueries';

/**
 * 运营端侧栏菜单：消费后端 `capabilities.menus`（sys_menu 解析树）。
 * 节点 / 分组 / 排序 / 可见性全由后端按权限解析，前端只把树拍平成 AppShell 的扁平菜单 + 映射图标。
 */
export function OpsAppShell({ children }: { children: ReactNode }) {
  const navigate = useNavigate();
  const location = useLocation();
  const user = useAuthStore((s) => s.user);
  // 全局头像（右上角 / 左下角）数据源；更换头像后 profileKeys.basic 失效 → 自动同步（O5-2）
  const profileBasic = useBasicQuery();
  const menus = useMenus();

  const menu = useMemo(() => flattenNavTree(menus), [menus]);
  const selectedKey = selectNavKey(menu, location.pathname, '/tenants');

  return (
    <AppShell
      density="ops"
      brand={{ title: 'Wen SaaS Boot', subtitle: '运营后台' }}
      menu={menu}
      selectedKey={selectedKey}
      onMenuClick={(key) => {
        void navigate(key);
      }}
      user={{
        name: user?.username ?? null,
        avatar: profileBasic.data?.avatarUrl ?? null,
        role: '运营账号',
      }}
      onUserBlockClick={() => {
        void navigate('/profile');
      }}
      onLogout={async () => {
        await authService.logout();
        useCapabilitiesStore.getState().reset();
        await navigate('/login', { replace: true });
      }}
      headerExtraLeft={
        <Breadcrumb crumbs={['Wen SaaS Boot 运营后台', navLabelOf(menu, selectedKey)]} />
      }
      notifications={{ unread: 0 }}
      onHelpClick={() => {
        // TODO: 接入帮助中心。当前仅为视觉占位。
      }}
    >
      <WatermarkLayer username={user?.username ?? null}>{children}</WatermarkLayer>
    </AppShell>
  );
}

function Breadcrumb({ crumbs }: { crumbs: ReactNode[] }) {
  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 6,
        fontSize: 13,
        color: 'var(--text-secondary)',
      }}
    >
      {crumbs.map((c, i) => (
        <Fragment key={i}>
          {i > 0 && (
            <RightOutlined style={{ fontSize: 10, color: 'var(--text-tertiary)' }} aria-hidden />
          )}
          <span
            style={{
              color: i === crumbs.length - 1 ? 'var(--text)' : 'inherit',
              fontWeight: i === crumbs.length - 1 ? 500 : 400,
            }}
          >
            {c}
          </span>
        </Fragment>
      ))}
    </div>
  );
}
