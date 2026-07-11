import { useEffect, useMemo } from 'react';
import { App as AntdApp, ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { RouterProvider } from 'react-router';
import { applyBrand, buildTenantTheme } from '@wen-saas/design-system';
import { startSessionWatcher } from '@wen-saas/auth';
import { router } from './router';
import { useUiPrefStore } from './lib/uiPref';
import { SessionLockOverlay } from './components/SessionLockOverlay';

export function App() {
  // 主题色双链路：applyBrand 翻 data-brand 驱动 CSS 变量（.wa-* 等），
  // buildTenantTheme 驱动 AntD 组件（AntD 不读 CSS 变量），两边同步才全站一致。
  const brand = useUiPrefStore((s) => s.brand);
  useEffect(() => applyBrand(brand), [brand]);
  const theme = useMemo(() => buildTenantTheme(brand), [brand]);

  useEffect(() => {
    // 在登录页时不弹会话锁（租户由子域名区分，登录页路径固定为 /login）。
    startSessionWatcher(() => window.location.pathname === '/login');
  }, []);

  return (
    <ConfigProvider locale={zhCN} theme={theme} button={{ autoInsertSpace: false }}>
      <AntdApp>
        <RouterProvider router={router} />
        <SessionLockOverlay />
      </AntdApp>
    </ConfigProvider>
  );
}
