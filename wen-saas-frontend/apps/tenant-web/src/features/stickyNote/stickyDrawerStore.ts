import { useSyncExternalStore } from 'react';

/**
 * 备忘便利贴抽屉的全局开关（零依赖外部 store）。
 *
 * 悬浮按钮（AppShell 挂载）与工作台卡片（WorkbenchPage）共享同一个抽屉：
 * 前者用 useStickyDrawerOpen + toggle 控制，后者用 openStickyDrawer 唤起。
 * app 层无 zustand 依赖，故用 useSyncExternalStore 自实现，避免引入新依赖。
 */

let open = false;
const listeners = new Set<() => void>();

function emit() {
  for (const l of listeners) l();
}

export function openStickyDrawer() {
  if (!open) {
    open = true;
    emit();
  }
}

export function closeStickyDrawer() {
  if (open) {
    open = false;
    emit();
  }
}

export function toggleStickyDrawer() {
  open = !open;
  emit();
}

export function useStickyDrawerOpen(): boolean {
  return useSyncExternalStore(
    (cb) => {
      listeners.add(cb);
      return () => listeners.delete(cb);
    },
    () => open,
    () => open,
  );
}
