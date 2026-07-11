import { useEffect, useState } from 'react';

/**
 * 主从/双栏页「宽→窄」的全站统一断点：视口 <1100px 视为窄屏
 * （工单 560+440 并排双栏再留侧栏/边距的临界值）。
 * 用区间语法 `width <` 保证恰好 1100px 仍走宽屏形态；不支持该语法的旧浏览器
 * matchMedia 恒不匹配，自然回落宽屏布局。
 */
export const NARROW_MEDIA_QUERY = '(width < 1100px)';

/**
 * 全站唯一的窄屏断点来源（matchMedia + change 监听）。
 * 窄屏下：定宽并排主从页详情改走 Drawer、固定左栏收起为 NavRailDrawer；
 * 宽屏（≥1100px）布局与现状一致。
 * 无 matchMedia 的环境（如未打补丁的 jsdom）恒返回 false（宽屏形态）。
 */
export function useIsNarrow(): boolean {
  const [narrow, setNarrow] = useState<boolean>(
    () =>
      typeof window !== 'undefined' &&
      typeof window.matchMedia === 'function' &&
      window.matchMedia(NARROW_MEDIA_QUERY).matches,
  );

  useEffect(() => {
    if (typeof window.matchMedia !== 'function') return undefined;
    const mq = window.matchMedia(NARROW_MEDIA_QUERY);
    const onChange = () => setNarrow(mq.matches);
    onChange();
    mq.addEventListener('change', onChange);
    return () => mq.removeEventListener('change', onChange);
  }, []);

  return narrow;
}
