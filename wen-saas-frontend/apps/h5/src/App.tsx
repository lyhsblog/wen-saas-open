import { useEffect } from 'react';
import { RouterProvider } from 'react-router';
import { startSessionWatcher } from '@wen-saas/auth';
import { router } from './router';
import { SessionLockOverlay } from './components/SessionLockOverlay';

export function App() {
  useEffect(() => {
    // 在登录页时不弹会话锁（租户由子域名区分，登录页路径固定为 /login）。
    startSessionWatcher(() => window.location.pathname === '/login');
  }, []);

  return (
    <div className="h5-viewport">
      <RouterProvider router={router} />
      <SessionLockOverlay />
    </div>
  );
}
