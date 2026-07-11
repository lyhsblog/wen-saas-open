import { useCallback, useState } from 'react';
import type { TabItem } from './types';

/**
 * 关闭一批页签后，计算应被激活的页签 key。
 *
 * - 仅当被关闭集合包含当前活跃页签时才需要切换；否则返回当前 activeKey（调用方无需导航）。
 * - 优先取「原列表中活跃页签右侧最近的保留项」，其次左侧最近，最后回退到首个保留项。
 */
export function nextActiveAfterClose(
  prevTabs: TabItem[],
  remaining: TabItem[],
  activeKey: string,
): string | null {
  const stillActive = remaining.some((t) => t.key === activeKey);
  if (stillActive) return activeKey;
  if (remaining.length === 0) return null;
  const remainingKeys = new Set(remaining.map((t) => t.key));
  const idx = prevTabs.findIndex((t) => t.key === activeKey);
  // 向右找最近的保留项。
  for (let i = idx + 1; i < prevTabs.length; i++) {
    if (remainingKeys.has(prevTabs[i]!.key)) return prevTabs[i]!.key;
  }
  // 再向左找。
  for (let i = idx - 1; i >= 0; i--) {
    if (remainingKeys.has(prevTabs[i]!.key)) return prevTabs[i]!.key;
  }
  return remaining[0]!.key;
}

/**
 * 可关闭页签按「最久未访问优先」（LRU）排序，供达上限时推荐关闭。无 lastActiveAt 视为最久。
 * 不可关闭页签（首页等）不纳入。
 */
export function closableByLru(tabs: TabItem[]): TabItem[] {
  return tabs
    .filter((t) => t.closable !== false)
    .slice()
    .sort((a, b) => (a.lastActiveAt ?? 0) - (b.lastActiveAt ?? 0));
}

export type TabsController = {
  tabs: TabItem[];
  /**
   * 确保某页签存在（不存在则追加，存在则刷新标题 / closable）并记为「刚访问」（更新 lastActiveAt）。
   * 幂等，用于「导航即开页签」。保留已有的 dirty 标记。
   */
  open: (item: TabItem) => void;
  /** 设置某页签的「未保存」脏标记（页面表单态变化时调用）。 */
  setDirty: (key: string, dirty: boolean) => void;
  /** 关闭单个页签；返回应激活的 key（null 表示已无页签）。不可关闭的页签忽略。 */
  close: (key: string, activeKey: string) => string | null;
  /** 关闭除 key 外的全部可关闭页签；返回应激活的 key。 */
  closeOthers: (key: string) => string | null;
  /** 关闭 key 右侧的全部可关闭页签；返回应激活的 key。 */
  closeRight: (key: string, activeKey: string) => string | null;
  /** 关闭全部可关闭页签（保留不可关闭项）；返回应激活的 key（null 表示已无页签）。 */
  closeAll: (activeKey: string) => string | null;
};

/**
 * 无路由依赖的页签状态容器。只负责维护「打开了哪些页签 + 各自的访问时间 / 脏标记」这一份
 * URL 无法表达的状态；「哪个页签活跃」由调用方依据 URL 派生并回传，关闭类操作返回应导航到的
 * key 交由调用方处理。
 */
export function useTabsController(initial: TabItem[] = []): TabsController {
  const [tabs, setTabs] = useState<TabItem[]>(initial);

  const open = useCallback((item: TabItem) => {
    setTabs((prev) => {
      const now = Date.now();
      const idx = prev.findIndex((t) => t.key === item.key);
      if (idx === -1) {
        const created: TabItem = { ...item, lastActiveAt: now };
        return [...prev, created];
      }
      const cur = prev[idx]!;
      // 复用旧项、刷新标题 / closable / 访问时间，保留 dirty。
      const updated: TabItem = { ...cur, title: item.title, lastActiveAt: now };
      if (item.closable !== undefined) updated.closable = item.closable;
      const next = [...prev];
      next[idx] = updated;
      return next;
    });
  }, []);

  const setDirty = useCallback((key: string, dirty: boolean) => {
    setTabs((prev) => {
      const idx = prev.findIndex((t) => t.key === key);
      if (idx === -1 || (prev[idx]!.dirty ?? false) === dirty) return prev;
      const next = [...prev];
      next[idx] = { ...prev[idx]!, dirty };
      return next;
    });
  }, []);

  // 注：下一个激活 key 必须从当前 tabs 快照「同步」算并直接返回——若把它算在 setTabs 的
  // updater 回调里再 return，React 不保证 updater 在本函数返回前执行，返回值会是陈旧的旧
  // activeKey（tabs 最终正确但返回值错，调用方据此导航会落到已关闭页签）。
  const close = useCallback((key: string, activeKey: string): string | null => {
    const target = tabs.find((t) => t.key === key);
    if (!target || target.closable === false) return activeKey;
    const remaining = tabs.filter((t) => t.key !== key);
    setTabs(remaining);
    return nextActiveAfterClose(tabs, remaining, activeKey);
  }, [tabs]);

  const closeOthers = useCallback((key: string): string | null => {
    setTabs((prev) => prev.filter((t) => t.key === key || t.closable === false));
    return key;
  }, []);

  const closeRight = useCallback((key: string, activeKey: string): string | null => {
    const idx = tabs.findIndex((t) => t.key === key);
    if (idx === -1) return activeKey;
    const remaining = tabs.filter((t, i) => i <= idx || t.closable === false);
    setTabs(remaining);
    return nextActiveAfterClose(tabs, remaining, activeKey);
  }, [tabs]);

  const closeAll = useCallback((activeKey: string): string | null => {
    const remaining = tabs.filter((t) => t.closable === false);
    setTabs(remaining);
    return nextActiveAfterClose(tabs, remaining, activeKey);
  }, [tabs]);

  return { tabs, open, setDirty, close, closeOthers, closeRight, closeAll };
}
