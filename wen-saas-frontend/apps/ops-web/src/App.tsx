import { useEffect } from 'react';
import { App as AntdApp, ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { RouterProvider } from 'react-router';
import { opsTheme } from '@wen-saas/design-system';
import { startSessionWatcher } from '@wen-saas/auth';
import { router } from './router';
import { SessionLockOverlay } from './components/SessionLockOverlay';

export function App() {
  useEffect(() => {
    // 跨标签页身份 watcher：登录页不弹锁定浮层。
    startSessionWatcher(() => window.location.pathname === '/login');
  }, []);

  return (
    <ConfigProvider locale={zhCN} theme={opsTheme}>
      <AntdApp>
        <RouterProvider router={router} />
        <SessionLockOverlay />
      </AntdApp>
    </ConfigProvider>
  );
}
