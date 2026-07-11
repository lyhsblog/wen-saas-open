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
    // 能力包是内存态，须随会话失效一并清空，否则会残留到下一个会话（含代登录 impersonating / 越权能力）。
    useCapabilitiesStore.getState().reset();
    if (typeof window === 'undefined') return;
    if (window.location.pathname === '/login') return;
    const from = encodeURIComponent(window.location.pathname + window.location.search);
    window.location.replace(`/login?from=${from}`);
  },
});

export const authService = createAuthService(http);
