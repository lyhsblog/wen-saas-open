import { type CSSProperties, type ReactNode, useState } from 'react';
import { Badge } from 'antd';

/** button 复位样式：去掉 UA 默认边框底色，焦点环走全局 :focus-visible 兜底。 */
const buttonReset: CSSProperties = {
  border: 'none',
  background: 'transparent',
  cursor: 'pointer',
  fontFamily: 'inherit',
  textAlign: 'left',
};

export type NavItemProps = {
  active: boolean;
  /** 左侧图标（可选） */
  icon?: ReactNode;
  children: ReactNode;
  onClick: () => void;
  /** 右侧尾缀（计数芯片 / 徽标等），自动推到最右 */
  trailing?: ReactNode;
};

/**
 * 左栏导航项（个人中心 / 文件中心左栏同款）：36px 高、圆角 8、图标+文字左对齐。
 * 选中态 = 品牌浅底（--brand-bg）+ 品牌色文字 + 600 字重；未选中 hover 浅灰。
 * 全部走 CSS 令牌，随租户主题切换。
 */
export function NavItem({ active, icon, children, onClick, trailing }: NavItemProps) {
  const [hover, setHover] = useState(false);
  return (
    <button
      type="button"
      aria-current={active || undefined}
      onClick={onClick}
      onMouseEnter={() => setHover(true)}
      onMouseLeave={() => setHover(false)}
      style={{
        ...buttonReset,
        height: 36,
        display: 'flex',
        alignItems: 'center',
        gap: 8,
        padding: '0 12px',
        borderRadius: 8,
        fontSize: 14,
        width: '100%',
        background: active
          ? 'var(--brand-bg)'
          : hover
            ? 'var(--bg-muted, rgba(0, 0, 0, 0.04))'
            : 'transparent',
        color: active ? 'var(--brand)' : 'var(--text-secondary)',
        fontWeight: active ? 600 : 400,
      }}
    >
      {icon}
      <span style={{ flex: 1, minWidth: 0, overflow: 'hidden', textOverflow: 'ellipsis' }}>
        {children}
      </span>
      {trailing}
    </button>
  );
}

export type TabChipProps = {
  active: boolean;
  children: ReactNode;
  onClick: () => void;
  /** 未读数等徽标数字，0 / 空不渲染 */
  badge?: number;
};

/**
 * 行内页签芯片（消息中心分类页签 / 列表面板筛选页签同款）：padding 3px 10px、圆角 6，
 * 选中态与 NavItem 同一套品牌浅底令牌。role="tab" 语义，父容器建议加 role="tablist"。
 */
export function TabChip({ active, children, onClick, badge }: TabChipProps) {
  const [hover, setHover] = useState(false);
  return (
    <button
      type="button"
      role="tab"
      aria-selected={active}
      onClick={onClick}
      onMouseEnter={() => setHover(true)}
      onMouseLeave={() => setHover(false)}
      style={{
        ...buttonReset,
        display: 'inline-flex',
        alignItems: 'center',
        padding: '3px 10px',
        borderRadius: 6,
        fontSize: 13,
        background: active
          ? 'var(--brand-bg)'
          : hover
            ? 'var(--bg-muted, rgba(0, 0, 0, 0.04))'
            : 'transparent',
        color: active ? 'var(--brand)' : 'var(--text-secondary)',
        fontWeight: active ? 600 : 400,
      }}
    >
      {children}
      {badge ? <Badge count={badge} size="small" style={{ marginLeft: 4 }} /> : null}
    </button>
  );
}
