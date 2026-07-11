import {
  forwardRef,
  type CSSProperties,
  type HTMLAttributes,
  type MouseEvent,
  useEffect,
  useRef,
  useState,
} from 'react';
import { Dropdown, type MenuProps } from 'antd';
import { CloseOutlined } from '@ant-design/icons';
import type { TabContextAction, TabItem } from './types';

export type TabsBarProps = {
  items: TabItem[];
  activeKey: string;
  /** 点击页签（含点击当前活跃页签）。 */
  onActivate: (key: string) => void;
  /** 关闭单个页签（仅 closable 页签会渲染关闭按钮）。 */
  onClose: (key: string) => void;
  /** 右键菜单动作。 */
  onContext?: (action: TabContextAction, key: string) => void;
};

const BAR_HEIGHT = 38;

type TabChipProps = {
  item: TabItem;
  active: boolean;
  hovered: boolean;
  onActivate: () => void;
  onClose: () => void;
  onHoverChange: (v: boolean) => void;
} & HTMLAttributes<HTMLDivElement>;

// forwardRef + 透传 rest：作为 Dropdown(contextMenu) 的直接子元素，antd 会向子元素注入
// ref 与 onContextMenu 等 props，必须落到真实 DOM 上，否则右键菜单静默失效。
const TabChip = forwardRef<HTMLDivElement, TabChipProps>(function TabChip(
  { item, active, hovered, onActivate, onClose, onHoverChange, ...rest },
  ref,
) {
  const closable = item.closable !== false;
  const dirty = !!item.dirty;
  const base: CSSProperties = {
    position: 'relative',
    display: 'inline-flex',
    alignItems: 'center',
    gap: 6,
    height: 28,
    padding: closable ? '0 6px 0 12px' : '0 12px',
    borderRadius: 'var(--r-md)',
    fontSize: 13,
    lineHeight: 1,
    whiteSpace: 'nowrap',
    cursor: 'pointer',
    userSelect: 'none',
    border: '1px solid transparent',
    transition: 'background .15s, color .15s, border-color .15s, box-shadow .15s',
    // 活跃态：品牌浅底 + 品牌字（实心填充只留给主按钮），加粗保证一眼可辨；
    // 非活跃态给浅灰底 + 描边，让相邻页签各自成块、彼此有隔离感（hover 时浮起品牌浅底）。
    color: active || hovered ? 'var(--brand)' : 'var(--text-secondary)',
    background: active || hovered ? 'var(--brand-bg)' : 'var(--bg-muted)',
    borderColor: active || hovered ? 'var(--brand-bg-strong)' : 'var(--border)',
    boxShadow: 'none',
    fontWeight: active ? 600 : 500,
  };
  return (
    <div
      {...rest}
      ref={ref}
      role="tab"
      aria-selected={active}
      data-tab-key={item.key}
      title={typeof item.title === 'string' ? item.title : undefined}
      style={base}
      onClick={(e) => {
        rest.onClick?.(e);
        onActivate();
      }}
      onMouseEnter={(e) => {
        rest.onMouseEnter?.(e);
        onHoverChange(true);
      }}
      onMouseLeave={(e) => {
        rest.onMouseLeave?.(e);
        onHoverChange(false);
      }}
      onAuxClick={(e) => {
        // 鼠标中键关闭。
        if (e.button === 1 && closable) {
          e.preventDefault();
          onClose();
        }
      }}
    >
      {dirty && (
        <span
          aria-label="未保存"
          title="有未保存内容"
          style={{
            width: 7,
            height: 7,
            borderRadius: '50%',
            flexShrink: 0,
            background: 'var(--warning, #faad14)',
          }}
        />
      )}
      <span
        style={{
          maxWidth: 160,
          overflow: 'hidden',
          textOverflow: 'ellipsis',
          whiteSpace: 'nowrap',
        }}
      >
        {item.title}
      </span>
      {closable && (
        <span
          role="button"
          aria-label="关闭页签"
          onClick={(e: MouseEvent) => {
            e.stopPropagation();
            onClose();
          }}
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            justifyContent: 'center',
            width: 16,
            height: 16,
            borderRadius: 'var(--r-sm)',
            color: 'var(--text-tertiary)',
            transition: 'background .15s, color .15s',
          }}
          onMouseEnter={(e) => {
            e.currentTarget.style.background = 'var(--border-strong)';
            e.currentTarget.style.color = 'var(--text)';
          }}
          onMouseLeave={(e) => {
            e.currentTarget.style.background = 'transparent';
            e.currentTarget.style.color = 'var(--text-tertiary)';
          }}
        >
          <CloseOutlined style={{ fontSize: 10 }} />
        </span>
      )}
    </div>
  );
});

/**
 * 页签条。横向排列、可横向滚动、活跃态高亮（品牌浅底 + 品牌字）、可关闭页签带关闭按钮（中键亦可关）、
 * 右键弹出「关闭其他 / 关闭右侧 / 关闭全部」菜单。激活页签变化时自动滚动进可视区（溢出场景下
 * 始终能看到当前页签）。纯展示组件，状态与保活由上层负责。
 */
export function TabsBar({ items, activeKey, onActivate, onClose, onContext }: TabsBarProps) {
  const [hoverKey, setHoverKey] = useState<string | null>(null);
  const barRef = useRef<HTMLDivElement>(null);

  // 激活页签变化时把它横向滚入可视区；block:'nearest' 避免牵动整页纵向滚动。
  useEffect(() => {
    const bar = barRef.current;
    if (!bar || !activeKey) return;
    const sel = `[data-tab-key="${activeKey.replace(/["\\]/g, '\\$&')}"]`;
    const el = bar.querySelector<HTMLElement>(sel);
    el?.scrollIntoView({ behavior: 'smooth', block: 'nearest', inline: 'nearest' });
  }, [activeKey, items]);

  const buildContextMenu = (item: TabItem): MenuProps => {
    const idx = items.findIndex((t) => t.key === item.key);
    const hasClosableOthers = items.some((t) => t.key !== item.key && t.closable !== false);
    const hasClosableRight = items.some((t, i) => i > idx && t.closable !== false);
    const hasAnyClosable = items.some((t) => t.closable !== false);
    return {
      items: [
        { key: 'closeOthers', label: '关闭其他', disabled: !hasClosableOthers },
        { key: 'closeRight', label: '关闭右侧', disabled: !hasClosableRight },
        { type: 'divider' },
        { key: 'closeAll', label: '关闭全部', disabled: !hasAnyClosable },
      ],
      onClick: ({ key, domEvent }) => {
        domEvent.stopPropagation();
        onContext?.(key as TabContextAction, item.key);
      },
    };
  };

  return (
    <div
      ref={barRef}
      role="tablist"
      // wa-scrollbar-thin 来自 design-system tokens.css：4px 细滚动条、悬停才显色
      className="wa-scrollbar-thin"
      style={{
        height: BAR_HEIGHT,
        flexShrink: 0,
        display: 'flex',
        alignItems: 'center',
        gap: 6,
        padding: '0 12px',
        background: 'var(--bg-elev)',
        borderBottom: '1px solid var(--border)',
        overflowX: 'auto',
        overflowY: 'hidden',
        scrollbarWidth: 'thin',
      }}
    >
      {items.map((item) => {
        const chip = (
          <TabChip
            item={item}
            active={item.key === activeKey}
            hovered={hoverKey === item.key}
            onActivate={() => onActivate(item.key)}
            onClose={() => onClose(item.key)}
            onHoverChange={(v) => setHoverKey((k) => (v ? item.key : k === item.key ? null : k))}
          />
        );
        return onContext ? (
          <Dropdown key={item.key} trigger={['contextMenu']} menu={buildContextMenu(item)}>
            {chip}
          </Dropdown>
        ) : (
          <div key={item.key}>{chip}</div>
        );
      })}
    </div>
  );
}
