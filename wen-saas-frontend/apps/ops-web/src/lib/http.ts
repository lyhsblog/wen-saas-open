import { createApiClient } from '@wen-saas/api-client';
import {
  getAuthSnapshot,
  useAuthStore,
  createAuthService,
  useSessionLockStore,
} from '@wen-saas/auth';
import type { UserSummary } from '@wen-saas/domain';

export const http = createApiClient({
  baseURL: '',
  getAuth: getAuthSnapshot,
  // 刷新成功：写回新 access（同账号，user 不变；refresh 已由后端回种 cookie）。
  onAccessToken: (token, user) => useAuthStore.getState().setSession(token, user as UserSummary),
  // 静默刷新失败（refresh 过期/无效/被重放）：在当前页弹重认证锁定浮层，不硬跳登录（保表单）。
  onReauthRequired: () => {
    if (typeof window !== 'undefined' && window.location.pathname === '/login') return;
    useSessionLockStore.getState().lock('expired');
  },
  // 认证端点 401（如登录失败）/ 未配置刷新时的兜底：清态并回登录页。
  onUnauthorized: () => {
    useAuthStore.getState().clear();
    if (typeof window === 'undefined') return;
    if (window.location.pathname === '/login') return;
    const from = encodeURIComponent(window.location.pathname + window.location.search);
    window.location.replace(`/login?from=${from}`);
  },
});

export const authService = createAuthService(http);
