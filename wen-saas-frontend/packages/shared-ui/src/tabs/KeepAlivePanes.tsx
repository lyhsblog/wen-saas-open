import { type ReactNode, useState } from 'react';
import type { TabItem } from './types';
import { TabActiveProvider, TabKeyProvider } from './tabStatus';

export type KeepAlivePanesProps = {
  items: TabItem[];
  activeKey: string;
  /** 渲染某页签对应的页面内容。仅在该页签首次激活后才会被调用并保留挂载。 */
  renderTab: (key: string) => ReactNode;
};

/**
 * 保活页签内容区。已打开的页签内容常驻挂载、靠 CSS 显隐切换，
 * 因此非活跃页签的组件 state / 已填表单 / 已拉数据 / 分页等都不丢失；每个页签是独立滚动容器，
 * 滚动位置各自保留。仅「激活过」的页签才真正挂载（避免预置但未访问的页签被提前 mount）；
 * 页签关闭后即从 items 移除、对应内容随之卸载释放内存（mounted 里残留的 key 不在 items 中、
 * 不会渲染，无需额外清理）。
 *
 * 布局要求：需放在一个「定位 + 具体高度」的容器内（如 flex:1 + position:relative 的盒子，
 * 或被 antd Watermark 包裹的内容区），各页面板以 absolute inset:0 充满并各自滚动。
 */
export function KeepAlivePanes({ items, activeKey, renderTab }: KeepAlivePanesProps) {
  const [mounted, setMounted] = useState<string[]>(() => (activeKey ? [activeKey] : []));
  // 渲染期派生：活跃页签首次出现即记入挂载集合（React 认可的「渲染中调整 state」范式，
  // 下一帧即稳定，不进 effect 以免级联渲染）。
  if (activeKey && !mounted.includes(activeKey)) {
    setMounted([...mounted, activeKey]);
  }

  return (
    <div style={{ position: 'absolute', inset: 0 }}>
      {items.map((t) => {
        const active = t.key === activeKey;
        // 活跃页签首帧即挂载（不闪空白）；其余靠 mounted 集合保活。
        const isMounted = active || mounted.includes(t.key);
        return (
          <div
            key={t.key}
            role="tabpanel"
            aria-hidden={!active}
            style={{
              position: 'absolute',
              inset: 0,
              overflow: 'auto',
              display: active ? 'block' : 'none',
            }}
          >
            {isMounted ? (
              <TabKeyProvider value={t.key}>
                <TabActiveProvider value={active}>{renderTab(t.key)}</TabActiveProvider>
              </TabKeyProvider>
            ) : null}
          </div>
        );
      })}
    </div>
  );
}
