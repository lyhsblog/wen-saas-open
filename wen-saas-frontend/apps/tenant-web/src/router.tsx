import { createBrowserRouter } from 'react-router';
import { LoginPage } from './features/auth/LoginPage';
import { ResetPasswordPage } from './features/auth/ResetPasswordPage';
import { ImpersonateRedeemPage } from './features/auth/ImpersonateRedeemPage';
import { PublicSharePage } from './features/fileCenter/PublicSharePage';
import { TenantRootGate } from './components/TenantRootGate';

export const router = createBrowserRouter([
  // 免登录文件分享落地页（FC-901）：token 自携租户，任意子域可达。
  { path: '/s/:shareToken', element: <PublicSharePage /> },
  { path: '/login', element: <LoginPage /> },
  { path: '/reset-password/:token', element: <ResetPasswordPage /> },
  { path: '/impersonate-redeem', element: <ImpersonateRedeemPage /> },
  // 根与所有深层路径：交给 Gate 分流（AppShell 内部再按路径解析具体页面 / 兜 404）。
  { path: '*', element: <TenantRootGate /> },
]);
