import { createApiClient } from '@wen-saas/api-client';
import {
  getAuthSnapshot,
  useAuthStore,
  createAuthService,
  useSessionLockStore,
} from '@wen-saas/auth';
import type { UserSummary } from '@wen-saas/domain';
import { useCapabilitiesStore } from '@wen-saas/permissions';

function onLoginRoute(): boolean {
  if (typeof window === 'undefined') return false;
  return window.location.pathname === '/login';
}

// 与租户 PC 端同源同链路：短 access + httpOnly refresh cookie + Web Locks 静默刷新。
// H5 是独立子域（{租户}.m.example.com），cookie/storage 天然与 PC 隔离，不画代登录/双 token，
// 会话失效一律回本端登录页。
export const http = createApiClient({
  baseURL: '',
  getAuth: getAuthSnapshot,
  onAccessToken: (token, user) => useAuthStore.getState().setSession(token, user as UserSummary),
  onReauthRequired: () => {
    if (onLoginRoute()) return;
    useSessionLockStore.getState().lock('expired');
  },
  onUnauthorized: () => {
    useAuthStore.getState().clear();
    // 能力包是内存态，须随会话失效一并清空，否则残留到下一个会话。
    useCapabilitiesStore.getState().reset();
    if (typeof window === 'undefined') return;
    if (window.location.pathname === '/login') return;
    const from = encodeURIComponent(window.location.pathname + window.location.search);
    window.location.replace(`/login?from=${from}`);
  },
});

export const authService = createAuthService(http);
