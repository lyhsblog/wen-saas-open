import { type CSSProperties, type ReactNode, useState } from 'react';

/**
 * 节标题（普通条目无 key）。
 * - `collapsible`：点节头可折叠整组（不传即静态标题，ops 端沿用）。
 * - `defaultCollapsed`：初始折叠（仅 collapsible 生效）。含当前选中项的组会自动展开。
 */
export type SidebarSectionItem = {
  section: string;
  collapsible?: boolean;
  defaultCollapsed?: boolean;
};

/** 普通条目。 */
export type SidebarLinkItem = {
  key: string;
  label: ReactNode;
  icon?: ReactNode;
  disabled?: boolean;
  /** 'todo' / 'biz' 用于在原型中标记"待出"/"业务"占位，渲染为右侧小药丸。 */
  status?: 'todo' | 'biz';
  /** 右上角红色 badge 数量（>0 才渲染）。 */
  badge?: number;
  /** 自定义 statusHint，title 显示。 */
  statusHint?: string;
};

export type SidebarMenuItem = SidebarLinkItem | SidebarSectionItem;

export type SidebarMenuDensity = 'ops' | 'tenant';

export type SidebarMenuProps = {
  items: SidebarMenuItem[];
  selectedKey: string;
  onClick: (key: string) => void;
  /** ops = 暗色侧栏；tenant = 浅色侧栏。 */
  density?: SidebarMenuDensity;
};

type Palette = {
  text: string;
  textActive: string;
  activeBg: string;
  hoverBg: string;
  sectionText: string;
};

function palette(density: SidebarMenuDensity): Palette {
  if (density === 'ops') {
    return {
      text: 'var(--ops-side-text)',
      textActive: 'var(--ops-side-text-active)',
      activeBg: 'var(--ops-side-active-bg)',
      hoverBg: 'var(--ops-side-hover-bg)',
      sectionText: 'rgba(200, 204, 215, 0.55)',
    };
  }
  return {
    text: 'var(--tn-side-text)',
    textActive: 'var(--tn-side-text-active)',
    activeBg: 'var(--tn-side-active-bg)',
    hoverBg: 'var(--tn-side-hover-bg)',
    sectionText: 'var(--text-tertiary)',
  };
}

function isSection(item: SidebarMenuItem): item is SidebarSectionItem {
  return 'section' in item;
}

/** 把扁平 items 按节标题切成组；首个节标题之前的条目归入无标题首组。空组（被权限过滤光）自动丢弃。 */
type MenuGroup = { section: SidebarSectionItem | null; links: SidebarLinkItem[] };

function buildGroups(items: SidebarMenuItem[]): MenuGroup[] {
  const groups: MenuGroup[] = [];
  let current: MenuGroup = { section: null, links: [] };
  groups.push(current);
  for (const item of items) {
    if (isSection(item)) {
      current = { section: item, links: [] };
      groups.push(current);
    } else {
      current.links.push(item);
    }
  }
  return groups.filter((g) => g.links.length > 0);
}

function PillBadge({ kind, children }: { kind: 'todo' | 'biz'; children: ReactNode }) {
  const style: CSSProperties =
    kind === 'todo'
      ? {
          color: 'var(--text-tertiary)',
          background: 'rgba(255, 255, 255, 0.06)',
          border: '1px solid rgba(255, 255, 255, 0.08)',
        }
      : {
          color: 'var(--brand)',
          background: 'var(--brand-bg)',
          border: '1px solid var(--brand-bg-strong)',
        };
  return (
    <span
      style={{
        fontSize: 10,
        padding: '1px 6px',
        borderRadius: 'var(--r-pill)',
        lineHeight: '14px',
        ...style,
      }}
    >
      {children}
    </span>
  );
}

function DangerDot({ value }: { value: number }) {
  return (
    <span
      style={{
        minWidth: 16,
        height: 16,
        padding: '0 5px',
        borderRadius: 8,
        background: 'var(--danger)',
        color: '#fff',
        fontSize: 10,
        fontWeight: 600,
        display: 'inline-flex',
        alignItems: 'center',
        justifyContent: 'center',
      }}
    >
      {value > 99 ? '99+' : value}
    </span>
  );
}

/** 折叠箭头：collapsed 时指向右（▸），展开时指向下（▾）。 */
function CaretIcon({ collapsed, color }: { collapsed: boolean; color: string }) {
  return (
    <svg
      width="10"
      height="10"
      viewBox="0 0 10 10"
      aria-hidden
      style={{
        flexShrink: 0,
        transform: collapsed ? 'rotate(-90deg)' : 'none',
        transition: 'transform .15s',
      }}
    >
      <path
        d="M2 3.5 L5 6.5 L8 3.5"
        stroke={color}
        strokeWidth="1.4"
        fill="none"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

/**
 * 侧栏菜单。直接按原型实现以拿到节标题 / 待出占位 / 红色 badge / 浅深双色等细节；
 * 不再走 antd Menu，避免被 antd Menu 的 hover/active 默认样式覆盖。
 * 节标题可声明 `collapsible` 折叠整组；含当前选中项的组始终展开，避免藏住当前页。
 */
export function SidebarMenu({ items, selectedKey, onClick, density = 'tenant' }: SidebarMenuProps) {
  const p = palette(density);
  const [hoverKey, setHoverKey] = useState<string | null>(null);
  const [collapsedState, setCollapsedState] = useState<Record<string, boolean>>(() => {
    const init: Record<string, boolean> = {};
    for (const item of items) {
      if (isSection(item) && item.collapsible) init[item.section] = !!item.defaultCollapsed;
    }
    return init;
  });

  const groups = buildGroups(items);

  const renderLink = (item: SidebarLinkItem) => {
    const active = item.key === selectedKey;
    const hover = !active && hoverKey === item.key && !item.disabled;
    const dimmed = !!item.status && !active;
    return (
      <div
        key={item.key}
        onClick={() => {
          if (item.disabled) return;
          onClick(item.key);
        }}
        onMouseEnter={() => setHoverKey(item.key)}
        onMouseLeave={() => setHoverKey((k) => (k === item.key ? null : k))}
        title={item.disabled ? (item.statusHint ?? '该页面尚未输出高保真稿') : undefined}
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 10,
          padding: '6px 10px',
          margin: '1px 0',
          borderRadius: 'var(--r-md)',
          color: active ? p.textActive : p.text,
          background: active ? p.activeBg : hover ? p.hoverBg : 'transparent',
          fontWeight: active ? 600 : 500,
          fontSize: 13,
          lineHeight: 1.6,
          cursor: item.disabled ? 'not-allowed' : 'pointer',
          opacity: dimmed ? 0.7 : 1,
          transition: 'background .15s, color .15s',
        }}
      >
        {item.icon != null && (
          <span
            style={{
              width: 16,
              height: 16,
              display: 'inline-flex',
              alignItems: 'center',
              justifyContent: 'center',
              flexShrink: 0,
            }}
          >
            {item.icon}
          </span>
        )}
        <span
          style={{
            flex: 1,
            minWidth: 0,
            whiteSpace: 'nowrap',
            overflow: 'hidden',
            textOverflow: 'ellipsis',
          }}
        >
          {item.label}
        </span>
        {item.badge != null && item.badge > 0 && !item.status && <DangerDot value={item.badge} />}
        {item.status === 'todo' && <PillBadge kind="todo">待出</PillBadge>}
        {item.status === 'biz' && <PillBadge kind="biz">业务</PillBadge>}
      </div>
    );
  };

  return (
    <nav style={{ flex: 1, padding: '6px 8px', overflowY: 'auto' }}>
      {groups.map((g, gi) => {
        const sec = g.section;
        const containsActive = g.links.some((l) => l.key === selectedKey);
        const collapsible = !!sec?.collapsible;
        // 含当前选中项时强制展开，避免折叠藏住当前页。
        const collapsed = collapsible
          ? (collapsedState[sec.section] ?? false) && !containsActive
          : false;

        return (
          <div key={sec ? `section-${sec.section}` : `group-${gi}`}>
            {sec && (
              <div
                onClick={
                  collapsible
                    ? () => setCollapsedState((s) => ({ ...s, [sec.section]: !s[sec.section] }))
                    : undefined
                }
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 6,
                  fontSize: 10,
                  color: p.sectionText,
                  padding: '12px 10px 4px',
                  letterSpacing: 0.6,
                  userSelect: 'none',
                  cursor: collapsible ? 'pointer' : 'default',
                }}
              >
                {collapsible && <CaretIcon collapsed={collapsed} color={p.sectionText} />}
                <span style={{ flex: 1 }}>{sec.section}</span>
              </div>
            )}
            {!collapsed && g.links.map(renderLink)}
          </div>
        );
      })}
    </nav>
  );
}
