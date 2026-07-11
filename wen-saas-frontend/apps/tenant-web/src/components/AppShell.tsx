import { type ReactNode, useEffect, useMemo, useState } from 'react';
import { App } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { AuditOutlined, KeyOutlined, SyncOutlined, UserOutlined } from '@ant-design/icons';
import { Navigate, useLocation, useNavigate } from 'react-router';
import { useAuthStore } from '@wen-saas/auth';
import {
  ReadonlyBanner,
  WatermarkLayer,
  useCapabilities,
  useCapabilitiesStore,
} from '@wen-saas/permissions';
import {
  AppShell as SharedAppShell,
  KeepAlivePanes,
  TabsBar,
  TabOverflowModal,
  TabStatusProvider,
  flattenNavTree,
  useTabsController,
  type AppShellMenuItem,
  type TabContextAction,
} from '@wen-saas/shared-ui';
import { publicTenantApi } from '@wen-saas/api-client';
import { authService, http } from '../lib/http';
import { resolvePageElement } from '../lib/page-routes';
import { tenantPath, useTenantKey } from '../lib/tenant-path';
import { useUnreadCount } from '../features/messageInbox/messageInboxQueries';
import { useBasic } from '../features/profile/profileQueries';
import { StickyNoteLauncher } from '../features/stickyNote/StickyNoteLauncher';
import { ImpersonationBanner } from './ImpersonationBanner';
import { ThemePaletteButton } from './ThemeSwatches';

/** 系统管理分组默认收起（其余分组默认展开）。分组顺序由后端 sys_menu 的 sort 决定，前端不再排。 */
const SYSTEM_SECTION = '系统管理';

/** 工作台作为首页常驻、不可关闭。 */
const HOME_PATH = '/workbench';

/** 页签数量软上限：超过即弹窗请用户手动挑选关闭（不自动关）。 */
const MAX_TABS = 12;

/** 不挂侧栏菜单、经顶部用户卡进入的页面的页签标题。 */
const EXTRA_TAB_TITLES: Record<string, string> = {
  '/profile': '个人中心',
  '/tasks': '我的导入导出',
  '/operation-logs': '我的操作记录',
};

/** 顺着汇编好的菜单找选中项的「分组 / 页面名」；无分组项 section 为 null。 */
function findCrumb(
  menu: AppShellMenuItem[],
  selectedKey: string,
): { section: string | null; label: ReactNode } | null {
  let section: string | null = null;
  for (const item of menu) {
    if ('section' in item) {
      section = item.section;
    } else if (item.key === selectedKey) {
      return { section, label: item.label };
    }
  }
  return null;
}

export function AppShell() {
  const navigate = useNavigate();
  const location = useLocation();
  const tenantKey = useTenantKey();
  const user = useAuthStore((s) => s.user);
  const caps = useCapabilities();
  // 全局头像（右上角 / 左下角）数据源；头像更换后 profileKeys 失效 → 此处自动同步（O5-2）
  const profileBasic = useBasic();
  const unreadQuery = useUnreadCount();
  const tabs = useTabsController([{ key: HOME_PATH, title: '工作台', closable: false }]);
  const { modal } = App.useApp();
  // 公开租户信息（名称 / Logo）。与 LoginPage 同 queryKey，命中共享缓存、不重复请求。
  const publicTenant = useQuery({
    queryKey: ['public-tenant', tenantKey],
    queryFn: () => publicTenantApi(http).byCode(tenantKey),
    enabled: !!tenantKey,
    retry: false,
    staleTime: 5 * 60 * 1000,
  }).data;

  const menu = useMemo<AppShellMenuItem[]>(() => {
    // 消费后端 capabilities.menus（sys_menu 解析树）：节点 / 分组 / 排序 / 可见性
    // 全由后端按权限解析，前端只拍平 + 补"分组可折叠、系统管理默认收起"的 UX，不再硬编码模块菜单。
    const flat = flattenNavTree(caps?.menus ?? []);
    return (
      flat
        // 个人中心不挂侧栏（经右上角用户卡进入）；它在 sys_menu 里是无码顶层页，这里前端过滤
        .filter((m) => !('key' in m) || m.key !== '/profile')
        .map((m) =>
          'section' in m
            ? { ...m, collapsible: true, defaultCollapsed: m.section === SYSTEM_SECTION }
            : m,
        )
    );
  }, [caps?.menus]);

  // 路径不再含租户段（租户在子域名），当前路径即相对页面路径。
  const relativePath = location.pathname || '/';
  const linkItems = menu.filter(
    (m): m is AppShellMenuItem & { key: string; label: ReactNode } => 'key' in m,
  );
  const selectedKey =
    linkItems.find((m) => relativePath === m.key || relativePath.startsWith(`${m.key}/`))?.key ??
    linkItems[0]?.key ??
    '';

  // 页签标题：优先取菜单标签（精确命中的菜单项），其次顶部卡进入的页面映射，最后取末段路径。
  const resolveTabTitle = (path: string): string => {
    const menuItem = linkItems.find((m) => m.key === path);
    if (menuItem && typeof menuItem.label === 'string') return menuItem.label;
    if (EXTRA_TAB_TITLES[path]) return EXTRA_TAB_TITLES[path];
    return path.split('/').filter(Boolean).pop() ?? path;
  };

  // 导航即开页签：当前路径登记成页签（已存在则刷新标题）。'/' 交给下方重定向，不登记。
  const activeKey = relativePath === '/' ? HOME_PATH : relativePath;
  useEffect(() => {
    if (relativePath === '/') return;
    tabs.open({
      key: relativePath,
      title: resolveTabTitle(relativePath),
      closable: relativePath !== HOME_PATH,
    });
    // resolveTabTitle 依赖 menu/linkItems；menu 变化时标题会随 open 的幂等更新刷新。
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [relativePath, menu]);

  const goTab = (key: string | null) => {
    void navigate(tenantPath(tenantKey, key ?? HOME_PATH));
  };

  // 关闭一批页签前若含「未保存」页签则二次确认，确认 / 无脏页签时执行 run。
  const confirmThenClose = (keys: string[], run: () => void) => {
    const dirtyTitles = tabs.tabs
      .filter((t) => keys.includes(t.key) && t.dirty)
      .map((t) => (typeof t.title === 'string' ? t.title : '页签'));
    if (dirtyTitles.length === 0) {
      run();
      return;
    }
    modal.confirm({
      title: '有未保存的内容',
      content: `「${dirtyTitles.join('、')}」尚未保存，关闭后将丢失。确认关闭？`,
      okText: '仍然关闭',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: run,
    });
  };

  const closeOne = (key: string) =>
    confirmThenClose([key], () => {
      const next = tabs.close(key, activeKey);
      if (next !== activeKey) goTab(next);
    });

  const onContext = (action: TabContextAction, key: string) => {
    const idx = tabs.tabs.findIndex((t) => t.key === key);
    const closing = tabs.tabs
      .filter((t, i) => {
        if (t.closable === false) return false;
        if (action === 'closeOthers') return t.key !== key;
        if (action === 'closeRight') return i > idx;
        return true; // closeAll
      })
      .map((t) => t.key);
    confirmThenClose(closing, () => {
      const next =
        action === 'closeOthers'
          ? tabs.closeOthers(key)
          : action === 'closeRight'
            ? tabs.closeRight(key, activeKey)
            : tabs.closeAll(activeKey);
      if (next !== activeKey) goTab(next);
    });
  };

  // 页签数软上限：超过即弹窗请用户手动挑选关闭。用「渲染期派生」而非 effect 控制弹出，
  // 避免级联渲染：新增页签越过上限即重新提示，回落到上限内复位「已忽略」。
  const count = tabs.tabs.length;
  const [seenCount, setSeenCount] = useState(count);
  const [overflowDismissed, setOverflowDismissed] = useState(false);
  if (seenCount !== count) {
    setSeenCount(count);
    if (count <= MAX_TABS || count > seenCount) setOverflowDismissed(false);
  }
  const overflowOpen = count > MAX_TABS && !overflowDismissed;

  // 任一页签未保存时，拦截整页关闭 / 刷新，避免丢失填写内容。
  const anyDirty = tabs.tabs.some((t) => t.dirty);
  useEffect(() => {
    if (!anyDirty) return;
    const handler = (e: BeforeUnloadEvent) => {
      e.preventDefault();
      e.returnValue = '';
    };
    window.addEventListener('beforeunload', handler);
    return () => window.removeEventListener('beforeunload', handler);
  }, [anyDirty]);

  // 工作台首页重定向（替代旧 TenantHome 路由）。
  if (relativePath === '/') {
    return <Navigate to={tenantPath(tenantKey, HOME_PATH)} replace />;
  }

  const crumb = findCrumb(menu, selectedKey);

  return (
    <SharedAppShell
      density="tenant"
      brand={{
        title: publicTenant?.name ?? tenantKey,
        subtitle: `/${tenantKey}`,
        logo: publicTenant?.logoUrl ? (
          <img
            src={publicTenant.logoUrl}
            alt=""
            width={26}
            height={26}
            style={{ borderRadius: 6, objectFit: 'cover', flexShrink: 0 }}
          />
        ) : undefined,
      }}
      menu={menu}
      selectedKey={selectedKey}
      onMenuClick={(key) => {
        void navigate(tenantPath(tenantKey, key));
      }}
      user={{
        name: caps?.realName?.trim() || (user?.username ?? null),
        avatar: profileBasic.data?.avatarUrl ?? null,
        role: caps?.tenantOwner ? '主管理员' : '成员',
        roleTone: caps?.tenantOwner ? 'danger' : 'brand',
        org: publicTenant?.name ?? null,
        account: user?.username ?? null,
      }}
      userMenuItems={[
        {
          key: 'profile',
          icon: <UserOutlined />,
          label: '个人中心',
          sub: '账号 / 安全',
          onClick: () => void navigate(tenantPath(tenantKey, '/profile')),
        },
        {
          key: 'pwd',
          icon: <KeyOutlined />,
          label: '修改密码',
          onClick: () => void navigate(tenantPath(tenantKey, '/profile?section=security')),
        },
        {
          key: 'tasks',
          icon: <SyncOutlined />,
          label: '我的导入导出',
          sub: '导入 / 导出任务',
          onClick: () => void navigate(tenantPath(tenantKey, '/tasks')),
        },
        {
          key: 'operation-logs',
          icon: <AuditOutlined />,
          label: '我的操作记录',
          sub: '查看本人操作记录',
          onClick: () => void navigate(tenantPath(tenantKey, '/operation-logs')),
        },
      ]}
      hideSidebarUserBlock
      notifications={{
        unread: unreadQuery.data?.total ?? 0,
        onClick: () => {
          void navigate(tenantPath(tenantKey, '/messages'));
        },
      }}
      onLogout={async () => {
        await authService.logout();
        useCapabilitiesStore.getState().reset();
        await navigate('/login', { replace: true });
      }}
      headerExtraLeft={
        crumb ? (
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 6,
              fontSize: 13,
              minWidth: 0,
            }}
          >
            {crumb.section && (
              <>
                <span style={{ color: 'var(--text-tertiary)' }}>{crumb.section}</span>
                <span style={{ color: 'var(--text-tertiary)' }}>/</span>
              </>
            )}
            <span style={{ color: 'var(--text)', fontWeight: 600 }}>{crumb.label}</span>
          </div>
        ) : undefined
      }
      headerExtraRight={<ThemePaletteButton />}
      tabBar={
        <TabsBar
          items={tabs.tabs}
          activeKey={activeKey}
          onActivate={(key) => goTab(key)}
          onClose={closeOne}
          onContext={onContext}
        />
      }
    >
      <ImpersonationBanner />
      <ReadonlyBanner />
      <StickyNoteLauncher />
      <div style={{ flex: 1, minHeight: 0, position: 'relative' }}>
        <WatermarkLayer username={user?.username ?? null}>
          <TabStatusProvider setDirty={tabs.setDirty}>
            <KeepAlivePanes
              items={tabs.tabs}
              activeKey={activeKey}
              renderTab={(key) => resolvePageElement(key)}
            />
          </TabStatusProvider>
        </WatermarkLayer>
      </div>
      <TabOverflowModal
        open={overflowOpen}
        count={count}
        max={MAX_TABS}
        tabs={tabs.tabs}
        excludeKey={activeKey}
        onCloseTab={closeOne}
        onDismiss={() => setOverflowDismissed(true)}
      />
    </SharedAppShell>
  );
}
