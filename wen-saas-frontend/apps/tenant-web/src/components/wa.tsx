import { forwardRef, type CSSProperties, type ReactNode } from 'react';
import { Button, type ButtonProps } from 'antd';

/**
 * 租户后台「系统管理」展示原语：Page / Card / Btn / Lbl / KV。
 *
 * 组件来源收敛约定：AntD 是唯一交互组件来源——交互控件只有 WaBtn（AntD Button 薄封装，
 * 视觉随 ConfigProvider 主题走）；其余为纯布局/排版结构，走 `tokens.css` 的 .wa-* 布局
 * 工具类（名录见该文件 Components 节顶部注释）。徽章统一用 @wen-saas/shared-ui 的
 * StatusPill / CountChip；裸 wa-btn / wa-input / wa-select 类有 eslint 卡口禁止使用。
 */

/** 页面顶部条：标题 + 面包屑/说明 + 右侧操作。 */
export function WaPageHead({
  title,
  hint,
  badges,
  actions,
}: {
  title: string;
  hint?: ReactNode;
  badges?: ReactNode;
  actions?: ReactNode;
}) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}>
      <div style={{ fontSize: 20, fontWeight: 600 }}>{title}</div>
      {hint != null && <span style={{ fontSize: 12, color: 'var(--text-tertiary)' }}>{hint}</span>}
      {badges}
      <div className="wa-spacer" />
      {actions}
    </div>
  );
}

/**
 * 页面外层容器：padding 24 + 纵向 16 间距 + 浅灰底 + 自身纵向滚动，对齐原型各系统页根节点。
 * 底部留 120px 安全边距：右下角常驻悬浮钮（AI 球 bottom:96 / 便利贴 bottom:24）为 position:fixed，
 * 内容需能滚到其上方、不被永久遮挡（评审 #14）。
 */
export function WaPage({ children }: { children: ReactNode }) {
  return (
    <div
      style={{
        padding: '24px 24px 120px',
        display: 'flex',
        flexDirection: 'column',
        gap: 16,
        background: 'var(--bg)',
        height: '100%',
        overflowY: 'auto',
      }}
    >
      {children}
    </div>
  );
}

/** Card：flush 时内容自带 14/20 内距并给标题加分隔线。 */
export function WaCard({
  title,
  extra,
  flush,
  padding,
  style,
  children,
}: {
  title?: ReactNode;
  extra?: ReactNode;
  flush?: boolean;
  padding?: number | string;
  style?: CSSProperties;
  children: ReactNode;
}) {
  const resolvedPadding = padding ?? (flush ? 0 : undefined);
  return (
    <div
      className={`wa-card${flush ? ' wa-card--flush' : ''}`}
      style={{ ...(resolvedPadding != null ? { padding: resolvedPadding } : {}), ...style }}
    >
      {title != null && (
        <div
          className="wa-card-head"
          style={{
            padding: flush ? '14px 20px' : '0 0 14px',
            borderBottom: flush ? '1px solid var(--divider)' : 'none',
            marginBottom: flush ? 0 : 14,
          }}
        >
          <div className="wa-card-title">{title}</div>
          {extra != null && <div className="wa-card-actions">{extra}</div>}
        </div>
      )}
      {flush ? <div style={{ padding: '14px 20px' }}>{children}</div> : children}
    </div>
  );
}

type WaBtnProps = {
  kind?: 'primary' | 'ghost';
  size?: 'sm' | 'lg';
  danger?: boolean;
  icon?: ReactNode;
} & Omit<ButtonProps, 'type' | 'size' | 'danger' | 'icon' | 'color' | 'variant'>;

/**
 * AntD Button 的薄封装：保持历史 kind/size API 供存量页面无感切换。
 * tenant 主题下高度体系与旧 .wa-btn 一致（默认 32 / sm 26 / lg 38），视觉随
 * ConfigProvider 主题（含主题色切换）走。forwardRef 使其可直接作为 antd
 * Tooltip / Popconfirm 等的 trigger。
 */
export const WaBtn = forwardRef<HTMLButtonElement, WaBtnProps>(function WaBtn(
  { kind, size, danger, icon, children, ...rest },
  ref,
) {
  return (
    <Button
      ref={ref}
      type={kind === 'primary' ? 'primary' : kind === 'ghost' ? 'text' : 'default'}
      size={size === 'sm' ? 'small' : size === 'lg' ? 'large' : 'middle'}
      danger={danger ?? false}
      icon={icon}
      {...rest}
    >
      {children}
    </Button>
  );
});

/** 两列网格中的左侧标签（对齐原型 Lbl）。 */
export function Lbl({ required, children }: { required?: boolean; children: ReactNode }) {
  return (
    <label style={{ fontSize: 13, fontWeight: 500, color: 'var(--text-secondary)', paddingTop: 8 }}>
      {children}
      {required && <span style={{ color: 'var(--danger)', marginLeft: 2 }}>*</span>}
    </label>
  );
}

/** key-value 行（对齐原型 KV）。 */
export function KV({
  k,
  v,
  mono,
  highlight,
}: {
  k: ReactNode;
  v: ReactNode;
  mono?: boolean;
  highlight?: boolean;
}) {
  return (
    <div style={{ display: 'flex', gap: 8, fontSize: 12, alignItems: 'baseline' }}>
      <span style={{ color: 'var(--text-tertiary)' }}>{k}：</span>
      <span
        style={{
          color: highlight ? 'var(--warning)' : 'var(--text)',
          fontFamily: mono ? 'var(--font-mono)' : 'inherit',
          fontWeight: highlight ? 600 : 400,
        }}
      >
        {v}
      </span>
    </div>
  );
}
