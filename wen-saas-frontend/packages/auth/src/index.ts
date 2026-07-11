import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';
import type { LoginRequest, LoginResponse, UserSummary } from '@wen-saas/domain';
import { authApi, type AuthSnapshot, type HttpClient } from '@wen-saas/api-client';

export type AuthState = {
  token: string | null;
  user: UserSummary | null;
};

type Actions = {
  setSession: (token: string, user: UserSummary) => void;
  clear: () => void;
};

const STORAGE_KEY = 'wen-saas:auth';

export const useAuthStore = create<AuthState & Actions>()(
  persist(
    (set) => ({
      token: null,
      user: null,
      setSession: (token, user) => set({ token, user }),
      clear: () => set({ token: null, user: null }),
    }),
    {
      name: STORAGE_KEY,
      storage: createJSONStorage(() => localStorage),
      partialize: (s) => ({ token: s.token, user: s.user }),
    },
  ),
);

export function getAuthSnapshot(): AuthSnapshot {
  const { token, user } = useAuthStore.getState();
  return { token, tenantId: user?.tenantId ?? null };
}

export function createAuthService(http: HttpClient) {
  const api = authApi(http);
  return {
    async login(body: LoginRequest): Promise<LoginResponse> {
      const res = await api.login(body);
      if (res.token && res.user) {
        useAuthStore.getState().setSession(res.token, res.user);
      }
      return res;
    },
    /** 登录期 MFA · 发起配置（首次强制）。凭 login 返回的 preAuthToken。 */
    mfaSetup: (body: { preAuthToken: string; method: 'TOTP' | 'SMS' | 'EMAIL' }) =>
      api.mfaSetup(body),
    /** 登录期 MFA · 发送动态码（已配置 SMS/EMAIL）。 */
    mfaChallenge: (body: { preAuthToken: string }) => api.mfaChallenge(body),
    /** 登录期 MFA · 校验并完成登录；成功即写入会话登录态。rememberMe 透传至 refresh token 有效期。 */
    async mfaVerify(body: { preAuthToken: string; code: string; rememberMe?: boolean }) {
      const res = await api.mfaVerify(body);
      if (res.token && res.user) {
        useAuthStore.getState().setSession(res.token, res.user);
      }
      return res;
    },
    /**
     * 第三方登录：已绑直登则写入会话登录态；未绑返回 bindingRequired=true + bindToken，
     * 调用方据此引导走 oauthBind 补账密绑定。
     */
    async oauthLogin(provider: string, body: { authCode: string; state?: string }) {
      const res = await api.oauthLogin(provider, body);
      if (!res.bindingRequired && res.token && res.user) {
        useAuthStore.getState().setSession(res.token, res.user);
      }
      return res;
    },
    /** 完成第三方绑定并登录；成功即写入会话登录态。 */
    async oauthBind(
      provider: string,
      body: { bindToken: string; username: string; password: string; tenantCode?: string },
    ): Promise<LoginResponse> {
      const res = await api.oauthBind(provider, body);
      if (res.token && res.user) {
        useAuthStore.getState().setSession(res.token, res.user);
      }
      return res;
    },
    async logout(): Promise<void> {
      try {
        await api.logout();
      } finally {
        useAuthStore.getState().clear();
      }
    },
    async refreshMe(): Promise<UserSummary> {
      const me = await api.me();
      const { token } = useAuthStore.getState();
      if (token) useAuthStore.getState().setSession(token, me);
      return me;
    },
  };
}

/* ============ 多标签页登录态：身份 watcher + 锁定浮层 store ============ */

/** 锁定原因：会话过期需重认证 / 别处登了别的账号 / 别处登出。 */
export type SessionLockReason = 'expired' | 'account-switched' | 'logged-out';

type SessionLockState = {
  locked: boolean;
  reason: SessionLockReason | null;
  /** 本标签页所属身份的展示名（重认证 / 登回提示用）。 */
  expectedName: string | null;
  /** account-switched 时，别处登入的新账号名。 */
  otherName: string | null;
  lock: (
    reason: SessionLockReason,
    info?: { expectedName?: string | null; otherName?: string | null },
  ) => void;
  unlock: () => void;
};

/** 锁定浮层状态。由 http 拦截器（expired）与身份 watcher（account-switched / logged-out）驱动。 */
export const useSessionLockStore = create<SessionLockState>((set) => ({
  locked: false,
  reason: null,
  expectedName: null,
  otherName: null,
  lock: (reason, info) =>
    set((s) => ({
      locked: true,
      reason,
      expectedName: info?.expectedName ?? s.expectedName,
      otherName: info?.otherName ?? null,
    })),
  unlock: () => set({ locked: false, reason: null, otherName: null }),
}));

/** 稳定身份键：运营端用 userId 即可；租户端用 tenantId:userId（同一人可跨租户）。 */
function userKey(user: UserSummary | null | undefined): string | null {
  if (!user || user.id == null) return null;
  return `${user.tenantId ?? ''}:${user.id}`;
}

type PersistedAuth = { token: string | null; user: UserSummary | null };

/** 直接读 localStorage 里的持久化登录态（zustand persist 的 envelope）。 */
function readPersistedAuth(): PersistedAuth {
  if (typeof localStorage === 'undefined') return { token: null, user: null };
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return { token: null, user: null };
    const env = JSON.parse(raw) as { state?: PersistedAuth };
    return { token: env.state?.token ?? null, user: env.state?.user ?? null };
  } catch {
    return { token: null, user: null };
  }
}

let watcherStarted = false;

/**
 * 启动跨标签页身份 watcher（应用根挂载时调一次）。监听 storage 事件 + 可见性变化，按稳定身份判定：
 * - 同身份、token 变 → 静默采用新 access（覆盖别标签页静默刷新 / 同账号重登），并在锁定态下解锁；
 * - 身份变了 → 锁定（account-switched）；
 * - 登录态被清空 → 锁定（logged-out）。
 *
 * @param isLoginRoute 判定当前是否在登录页（登录页不弹锁定浮层）。
 */
export function startSessionWatcher(isLoginRoute: () => boolean): void {
  if (watcherStarted || typeof window === 'undefined') return;
  watcherStarted = true;

  // 本标签页所属身份：以挂载时（已由 persist 水合）的登录态为准，随本页自身登录态变化更新。
  let expectedKey = userKey(useAuthStore.getState().user);
  let expectedName = useAuthStore.getState().user?.username ?? null;
  useSessionLockStore.setState({ expectedName });

  // 订阅本标签页内存态：本页自己的登录 / 切换（同标签 setSession，不会触发 storage 事件）
  // 即本页的“期望身份”，据此更新 expectedKey/expectedName，避免把自己刚登的账号误判成别处登入。
  // 跨标签变更只改 localStorage、不经过本页 setSession，故内存身份不会被它带偏。
  useAuthStore.subscribe((state) => {
    const selfKey = userKey(state.user);
    if (selfKey == null || selfKey === expectedKey) return;
    expectedKey = selfKey;
    expectedName = state.user?.username ?? null;
    useSessionLockStore.setState({ expectedName });
  });

  const recheck = (): void => {
    if (expectedKey == null) return; // 本就未登录，无需守护
    if (isLoginRoute()) return;
    const persisted = readPersistedAuth();
    const newKey = userKey(persisted.user);
    const lockStore = useSessionLockStore.getState();

    if (newKey == null) {
      if (!lockStore.locked) lockStore.lock('logged-out', { expectedName });
      return;
    }
    if (newKey !== expectedKey) {
      lockStore.lock('account-switched', {
        expectedName,
        otherName: persisted.user?.username ?? null,
      });
      return;
    }
    // 同身份：采用最新 access（覆盖别标签页静默刷新 / 同账号重登）；若处于锁定态（过期 / 账号切回）则解锁。
    // refresh 走 httpOnly cookie、浏览器同源 cookie jar 跨标签共享，无需再经 localStorage 同步。
    const cur = useAuthStore.getState();
    if (persisted.token && persisted.token !== cur.token && persisted.user) {
      useAuthStore.getState().setSession(persisted.token, persisted.user);
    }
    if (lockStore.locked) lockStore.unlock();
  };

  window.addEventListener('storage', (e: StorageEvent) => {
    if (e.key === STORAGE_KEY) recheck();
  });
  window.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'visible') recheck();
  });
  window.addEventListener('focus', recheck);
}
