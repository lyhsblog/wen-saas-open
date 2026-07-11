import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { UserSummary } from '@wen-saas/domain';

const STORAGE_KEY = 'wen-saas:auth';

/** 写入 zustand persist 信封形态的登录态，供模块导入时水合。 */
function seedPersisted(user: UserSummary | null, token = 'tok'): void {
  localStorage.setItem(STORAGE_KEY, JSON.stringify({ state: { token, user }, version: 0 }));
}

const tyAdmin: UserSummary = { id: '1001', username: 'ty-admin', tenantId: '9701' };
const uiAdmin: UserSummary = { id: '2002', username: 'uiadmin', tenantId: '9701' };

/** 每个用例拿到全新模块实例（重置 watcherStarted 单例 + store），并预置 localStorage。 */
async function loadFresh(seed: UserSummary | null) {
  vi.resetModules();
  localStorage.clear();
  if (seed) seedPersisted(seed);
  return import('./index');
}

describe('startSessionWatcher 跨标签身份守护', () => {
  beforeEach(() => {
    localStorage.clear();
  });
  afterEach(() => {
    localStorage.clear();
  });

  it('本标签页自身登录新账号（同标签 setSession）不应误判为别处切换', async () => {
    // 挂载时残留上一会话的 ty-admin（关浏览器未登出）
    const mod = await loadFresh(tyAdmin);
    mod.startSessionWatcher(() => false);

    // 本标签页登录页登入 uiadmin —— 同标签 setSession，不触发 storage 事件
    mod.useAuthStore.getState().setSession('tok2', uiAdmin);
    // 登录后页面获得焦点，触发 recheck
    window.dispatchEvent(new Event('focus'));

    expect(mod.useSessionLockStore.getState().locked).toBe(false);
    expect(mod.useSessionLockStore.getState().expectedName).toBe('uiadmin');
  });

  it('其它标签页登入别的账号（仅改 localStorage + storage 事件）仍应锁定', async () => {
    const mod = await loadFresh(tyAdmin);
    mod.startSessionWatcher(() => false);

    // 模拟另一标签页登入 uiadmin：只改共享 localStorage，本页内存态不变
    seedPersisted(uiAdmin, 'tok2');
    window.dispatchEvent(new StorageEvent('storage', { key: STORAGE_KEY }));

    const lock = mod.useSessionLockStore.getState();
    expect(lock.locked).toBe(true);
    expect(lock.reason).toBe('account-switched');
    expect(lock.expectedName).toBe('ty-admin');
    expect(lock.otherName).toBe('uiadmin');
  });

  it('其它标签页登出（清空 localStorage）应锁定为 logged-out', async () => {
    const mod = await loadFresh(tyAdmin);
    mod.startSessionWatcher(() => false);

    localStorage.removeItem(STORAGE_KEY);
    window.dispatchEvent(new StorageEvent('storage', { key: STORAGE_KEY }));

    const lock = mod.useSessionLockStore.getState();
    expect(lock.locked).toBe(true);
    expect(lock.reason).toBe('logged-out');
  });
});
