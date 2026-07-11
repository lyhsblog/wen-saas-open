import { type ReactNode, useEffect, useRef, useState } from 'react';
import { Avatar } from 'antd';
import {
  BellOutlined,
  LogoutOutlined,
  QuestionCircleOutlined,
  RightOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { SidebarMenu, type SidebarMenuItem } from './SidebarMenu';

export type Density = 'ops' | 'tenant';

export type AppShellMenuItem = SidebarMenuItem;

export type AppShellBrand = {
  /** 主标，第一行（如 "Wen SaaS Boot"）。 */
  title: string;
  /** 副标，第二行（如 "运营后台"）。可选，提供时按双行排版。 */
  subtitle?: string;
  /** logo 节点，未提供时使用默认 26×26 圆角矩形 + 描线 W。 */
  logo?: ReactNode;
};

export type AppShellUser = {
  name?: string | null;
  /** 头像图片 URL；缺省/加载失败时回退为姓名首字母。全局 chrome（右上角 / 左下角）共用。 */
  avatar?: string | null;
  /** 显示在侧栏底部用户块的角色名（如「客户经理」「主管理员」）。 */
  role?: string | null;
  /** 角色徽标色调：主管理员等高权限用 danger，其余 brand。默认 brand。 */
  roleTone?: 'danger' | 'brand';
  /** 显示在用户头像卡详情区的组织名（可选）。 */
  org?: string | null;
  /** 显示在用户头像卡详情区的登录账号（可选）。 */
  account?: string | null;
};

/** 右上角用户卡的菜单项（个人中心 / 修改密码 / 我的导入导出 / 我的操作记录 等）。 */
export type AppShellUserMenuItem = {
  key: string;
  icon?: ReactNode;
  label: string;
  /** 次要说明，灰字小号显示在标签下方。 */
  sub?: string;
  onClick: () => void;
};

export type AppShellNotifications = {
  /** 未读数；0 / undefined 时不显示红点。 */
  unread?: number;
  /** 点击铃铛回调，未提供则禁用。 */
  onClick?: () => void;
};

export type AppShellProps = {
  density?: Density;
  brand?: AppShellBrand;
  menu: AppShellMenuItem[];
  selectedKey: string;
  onMenuClick: (key: string) => void;
  user?: AppShellUser;
  onLogout?: () => void | Promise<void>;
  /** 右上角用户卡的菜单项；为空时卡片只含用户概览 + 退出。 */
  userMenuItems?: AppShellUserMenuItem[];
  /** 点击侧栏底部用户块。未提供则不可点击。 */
  onUserBlockClick?: () => void;
  /** 隐藏侧栏底部用户块（用户信息已收敛到右上角卡片时设 true）。 */
  hideSidebarUserBlock?: boolean;
  /** 顶部铃铛。未提供则不渲染。 */
  notifications?: AppShellNotifications;
  /** 顶部帮助按钮点击。未提供则不渲染。 */
  onHelpClick?: () => void;
  /** 头部左侧附加节点（如租户标签 / 面包屑）。 */
  headerExtraLeft?: ReactNode;
  /** 头部右侧、用户头像之前的附加节点。 */
  headerExtraRight?: ReactNode;
  /**
   * 头部与主内容区之间的页签条。提供时主内容区切换为 flex 纵向布局且自身不滚动
   * （由 children 内部各页面板独立滚动，配合 KeepAlivePanes 保活）。
   */
  tabBar?: ReactNode;
  children: ReactNode;
};

const SIDEBAR_WIDTH = 220;
const HEADER_HEIGHT = 52;
const BRAND_HEIGHT = 60;

const DEFAULT_LOGO = (
  <svg width="26" height="26" viewBox="0 0 26 26" aria-hidden style={{ flexShrink: 0 }}>
    <rect x="1" y="1" width="24" height="24" rx="6" fill="var(--brand)" />
    <path
      d="M7 9 L11 17 L13 11 L15 17 L19 9"
      stroke="#fff"
      strokeWidth="2"
      fill="none"
      strokeLinecap="round"
      strokeLinejoin="round"
    />
  </svg>
);

function HeaderIconButton({
  icon,
  onClick,
  title,
  disabled,
  badge,
}: {
  icon: ReactNode;
  onClick: (() => void) | undefined;
  title?: string;
  disabled?: boolean;
  badge: number | undefined;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      title={title}
      style={{
        position: 'relative',
        width: 32,
        height: 32,
        border: 'none',
        background: 'transparent',
        borderRadius: 'var(--r-md)',
        cursor: disabled ? 'not-allowed' : 'pointer',
        color: 'var(--text-secondary)',
        display: 'inline-flex',
        alignItems: 'center',
        justifyContent: 'center',
        transition: 'background .15s',
      }}
      onMouseEnter={(e) => {
        if (!disabled) e.currentTarget.style.background = 'var(--bg-muted)';
      }}
      onMouseLeave={(e) => {
        e.currentTarget.style.background = 'transparent';
      }}
    >
      {icon}
      {badge != null && badge > 0 && (
        <span
          style={{
            position: 'absolute',
            top: 4,
            right: 4,
            minWidth: 14,
            height: 14,
            padding: '0 3px',
            borderRadius: 8,
            background: 'var(--danger)',
            color: '#fff',
            fontSize: 9,
            fontWeight: 600,
            display: 'inline-flex',
            alignItems: 'center',
            justifyContent: 'center',
          }}
        >
          {badge > 99 ? '99+' : badge}
        </span>
      )}
    </button>
  );
}

function SidebarUserBlock({
  density,
  user,
  onClick,
}: {
  density: Density;
  user: AppShellUser;
  onClick: (() => void) | undefined;
}) {
  const isOps = density === 'ops';
  const textColor = isOps ? 'var(--ops-side-text-active)' : 'var(--text)';
  const subColor = isOps ? 'var(--ops-side-text)' : 'var(--text-tertiary)';
  const divider = isOps ? 'var(--ops-side-divider)' : 'var(--divider)';

  return (
    <div
      onClick={onClick}
      style={{
        padding: '12px 14px',
        borderTop: `1px solid ${divider}`,
        display: 'flex',
        alignItems: 'center',
        gap: 10,
        cursor: onClick ? 'pointer' : 'default',
      }}
    >
      <Avatar
        size={28}
        src={user.avatar ?? undefined}
        style={{
          background: 'var(--brand)',
          fontSize: 12,
          fontWeight: 600,
          flexShrink: 0,
        }}
      >
        {(user.name ?? '?').slice(0, 1)}
      </Avatar>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div
          style={{
            fontSize: 13,
            fontWeight: 500,
            color: textColor,
            whiteSpace: 'nowrap',
            overflow: 'hidden',
            textOverflow: 'ellipsis',
          }}
        >
          {user.name ?? '未登录'}
        </div>
        <div
          style={{
            fontSize: 11,
            color: subColor,
            whiteSpace: 'nowrap',
            overflow: 'hidden',
            textOverflow: 'ellipsis',
          }}
        >
          {user.role ?? (isOps ? '运营' : '租户用户')}
        </div>
      </div>
      <RightOutlined style={{ fontSize: 11, color: subColor }} />
    </div>
  );
}

/**
 * 右上角用户头像浮层卡：用户概览（头像 + 姓名 + 角色徽标 +
 * 组织 + 账号）+ 状态条（在线）+ 菜单项 + 红色退出。点击外部关闭。
 */
function HeaderUserMenu({
  user,
  items,
  onLogout,
}: {
  user: AppShellUser;
  items: AppShellUserMenuItem[];
  onLogout?: (() => void | Promise<void>) | undefined;
}) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!open) return;
    const onDoc = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', onDoc);
    return () => document.removeEventListener('mousedown', onDoc);
  }, [open]);

  const name = user.name ?? '未登录';
  const initial = name.slice(0, 1);
  const roleColor = user.roleTone === 'danger' ? 'var(--danger)' : 'var(--brand)';
  const roleBg = user.roleTone === 'danger' ? 'var(--danger-bg)' : 'var(--brand-bg)';

  return (
    <div ref={ref} style={{ position: 'relative' }}>
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        style={{
          display: 'inline-flex',
          alignItems: 'center',
          gap: 8,
          cursor: 'pointer',
          padding: '4px 10px 4px 4px',
          borderRadius: 'var(--r-pill)',
          border: '1px solid transparent',
          background: open ? 'var(--bg-muted)' : 'transparent',
          transition: 'background .15s',
        }}
        onMouseEnter={(e) => {
          if (!open) e.currentTarget.style.background = 'var(--bg-muted)';
        }}
        onMouseLeave={(e) => {
          if (!open) e.currentTarget.style.background = 'transparent';
        }}
      >
        <Avatar
          size={26}
          src={user.avatar ?? undefined}
          style={{ background: 'var(--brand)', fontSize: 12, fontWeight: 600 }}
        >
          {initial}
        </Avatar>
        <span style={{ fontSize: 13, fontWeight: 500, color: 'var(--text)' }}>{name}</span>
      </button>

      {open && (
        <div
          style={{
            position: 'absolute',
            top: 'calc(100% + 6px)',
            right: 0,
            zIndex: 1000,
            width: 280,
            background: 'var(--bg-elev)',
            border: '1px solid var(--border)',
            borderRadius: 'var(--r-md)',
            boxShadow: 'var(--shadow-pop, 0 8px 24px rgba(0,0,0,0.12))',
            overflow: 'hidden',
          }}
        >
          <div
            style={{
              padding: '14px 16px',
              borderBottom: '1px solid var(--divider)',
              display: 'flex',
              alignItems: 'center',
              gap: 12,
            }}
          >
            <Avatar
              size={40}
              src={user.avatar ?? undefined}
              style={{ background: 'var(--brand)', fontSize: 16, fontWeight: 600, flexShrink: 0 }}
            >
              {initial}
            </Avatar>
            <div style={{ minWidth: 0, flex: 1 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                <span style={{ fontWeight: 600, fontSize: 14, color: 'var(--text)' }}>{name}</span>
                {user.role && (
                  <span
                    style={{
                      fontSize: 10,
                      padding: '1px 6px',
                      borderRadius: 'var(--r-pill)',
                      color: roleColor,
                      background: roleBg,
                      whiteSpace: 'nowrap',
                    }}
                  >
                    {user.role}
                  </span>
                )}
              </div>
              {user.org && (
                <div
                  style={{
                    fontSize: 11,
                    color: 'var(--text-tertiary)',
                    marginTop: 3,
                    whiteSpace: 'nowrap',
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                  }}
                >
                  {user.org}
                </div>
              )}
              {user.account && (
                <div
                  style={{
                    fontSize: 11,
                    color: 'var(--text-tertiary)',
                    marginTop: 2,
                    fontFamily: 'var(--font-mono, monospace)',
                    whiteSpace: 'nowrap',
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                  }}
                >
                  账号 {user.account}
                </div>
              )}
            </div>
          </div>

          <div
            style={{
              padding: '8px 16px',
              display: 'flex',
              alignItems: 'center',
              gap: 8,
              fontSize: 11,
              color: 'var(--text-tertiary)',
              borderBottom: '1px solid var(--divider)',
              background: 'var(--bg-muted)',
            }}
          >
            <span
              style={{ width: 6, height: 6, borderRadius: 3, background: 'var(--success)' }}
              aria-hidden
            />
            <span>在线</span>
          </div>

          {items.length > 0 && (
            <div style={{ padding: 6 }}>
              {items.map((it) => (
                <button
                  key={it.key}
                  type="button"
                  onClick={() => {
                    setOpen(false);
                    it.onClick();
                  }}
                  style={{
                    width: '100%',
                    padding: '8px 10px',
                    display: 'flex',
                    alignItems: 'center',
                    gap: 10,
                    background: 'transparent',
                    border: 'none',
                    cursor: 'pointer',
                    fontFamily: 'inherit',
                    textAlign: 'left',
                    borderRadius: 'var(--r-sm)',
                    color: 'var(--text)',
                  }}
                  onMouseEnter={(e) => (e.currentTarget.style.background = 'var(--bg-muted)')}
                  onMouseLeave={(e) => (e.currentTarget.style.background = 'transparent')}
                >
                  <span
                    style={{
                      width: 28,
                      height: 28,
                      borderRadius: 'var(--r-sm)',
                      background: 'var(--bg-muted)',
                      color: 'var(--text-secondary)',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      flexShrink: 0,
                    }}
                  >
                    {it.icon ?? <UserOutlined />}
                  </span>
                  <span style={{ flex: 1, minWidth: 0 }}>
                    <span style={{ display: 'block', fontSize: 13, fontWeight: 500 }}>
                      {it.label}
                    </span>
                    {it.sub && (
                      <span
                        style={{ display: 'block', fontSize: 11, color: 'var(--text-tertiary)' }}
                      >
                        {it.sub}
                      </span>
                    )}
                  </span>
                  <RightOutlined style={{ fontSize: 11, color: 'var(--text-tertiary)' }} />
                </button>
              ))}
            </div>
          )}

          {onLogout && (
            <div style={{ borderTop: '1px solid var(--divider)', padding: 6 }}>
              <button
                type="button"
                onClick={() => {
                  setOpen(false);
                  void onLogout();
                }}
                style={{
                  width: '100%',
                  padding: '10px 12px',
                  display: 'flex',
                  alignItems: 'center',
                  gap: 10,
                  background: 'transparent',
                  border: 'none',
                  cursor: 'pointer',
                  fontFamily: 'inherit',
                  color: 'var(--danger)',
                  borderRadius: 'var(--r-sm)',
                  fontSize: 13,
                  fontWeight: 500,
                }}
                onMouseEnter={(e) =>
                  (e.currentTarget.style.background = 'var(--danger-bg, rgba(255,77,79,0.08))')
                }
                onMouseLeave={(e) => (e.currentTarget.style.background = 'transparent')}
              >
                <LogoutOutlined />
                退出登录
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

export function AppShell({
  density = 'tenant',
  brand,
  menu,
  selectedKey,
  onMenuClick,
  user,
  onLogout,
  userMenuItems,
  onUserBlockClick,
  hideSidebarUserBlock,
  notifications,
  onHelpClick,
  headerExtraLeft,
  headerExtraRight,
  tabBar,
  children,
}: AppShellProps) {
  const isOps = density === 'ops';
  const sideBg = isOps ? 'var(--ops-side-bg)' : 'var(--tn-side-bg)';
  const divider = isOps ? 'var(--ops-side-divider)' : 'var(--divider)';
  const brandColor = isOps ? 'var(--ops-side-text-active)' : 'var(--text)';

  return (
    <div style={{ display: 'flex', height: '100vh', overflow: 'hidden' }}>
      <aside
        style={{
          width: SIDEBAR_WIDTH,
          background: sideBg,
          borderRight: isOps ? 'none' : '1px solid var(--border)',
          display: 'flex',
          flexDirection: 'column',
          flexShrink: 0,
        }}
      >
        <div
          style={{
            height: BRAND_HEIGHT,
            padding: '0 18px',
            display: 'flex',
            alignItems: 'center',
            gap: 10,
            borderBottom: `1px solid ${divider}`,
            flexShrink: 0,
          }}
        >
          {brand?.logo ?? DEFAULT_LOGO}
          <div style={{ minWidth: 0, lineHeight: 1.15 }}>
            <div
              style={{
                fontSize: 14,
                fontWeight: 700,
                color: brandColor,
                letterSpacing: 0.2,
                whiteSpace: 'nowrap',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
              }}
            >
              {brand?.title ?? 'Wen SaaS'}
            </div>
            {brand?.subtitle && (
              <div
                style={{
                  fontSize: 11,
                  color: 'var(--text-tertiary)',
                  fontWeight: 400,
                  whiteSpace: 'nowrap',
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                  marginTop: 2,
                }}
              >
                {brand.subtitle}
              </div>
            )}
          </div>
        </div>
        <SidebarMenu
          items={menu}
          selectedKey={selectedKey}
          onClick={onMenuClick}
          density={density}
        />
        {user && !hideSidebarUserBlock && (
          <SidebarUserBlock density={density} user={user} onClick={onUserBlockClick} />
        )}
      </aside>

      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', minWidth: 0 }}>
        <header
          style={{
            height: HEADER_HEIGHT,
            padding: '0 16px 0 24px',
            background: 'var(--bg-elev)',
            borderBottom: '1px solid var(--border)',
            display: 'flex',
            alignItems: 'center',
            gap: 12,
            flexShrink: 0,
          }}
        >
          {headerExtraLeft}
          <div style={{ flex: 1 }} />
          {headerExtraRight}
          {notifications && (
            <HeaderIconButton
              icon={<BellOutlined style={{ fontSize: 16 }} />}
              onClick={notifications.onClick}
              disabled={!notifications.onClick}
              title="通知"
              badge={notifications.unread}
            />
          )}
          {onHelpClick && (
            <HeaderIconButton
              icon={<QuestionCircleOutlined style={{ fontSize: 16 }} />}
              onClick={onHelpClick}
              title="帮助"
              badge={undefined}
            />
          )}
          {(user || onLogout) && (
            <HeaderUserMenu user={user ?? {}} items={userMenuItems ?? []} onLogout={onLogout} />
          )}
        </header>
        {tabBar}
        {/* 无页签条时：纵向 auto，内容超出视口时主区滚动（自管高度的页面用 height:100% 内部滚动）。
            有页签条时：主区改 flex 纵向、自身不滚，由 children 内各页面板（KeepAlivePanes）独立滚动，
            以保留每个页签各自的滚动位置。横向恒 hidden（宽表自身处理横向滚动）。 */}
        <main
          style={
            tabBar
              ? {
                  flex: 1,
                  minHeight: 0,
                  display: 'flex',
                  flexDirection: 'column',
                  overflow: 'hidden',
                  background: 'var(--bg)',
                }
              : { flex: 1, overflowY: 'auto', overflowX: 'hidden', background: 'var(--bg)' }
          }
        >
          {children}
        </main>
      </div>
    </div>
  );
}
