import type { CSSProperties, ReactNode } from 'react';
import { Button, Drawer } from 'antd';
import { MenuOutlined } from '@ant-design/icons';

export type NavRailDrawerProps = {
  open: boolean;
  onClose: () => void;
  /** 抽屉宽度（px），默认 300；实际取 min(width, 85vw) 防超窄视口溢出。 */
  width?: number;
  children: ReactNode;
};

/**
 * 窄屏（useIsNarrow）下承载页面固定左栏的左侧抽屉。
 * 不带自身头部——左栏内容自带标题/工具条；点遮罩或 Esc 关闭，选中项后由调用方主动关闭。
 * 栏内容需以 fluid 形态渲染（宽 100%、高 100%、去右分隔线）；
 * 栏外层若有 Spin 嵌套包装，用 wrapperClassName 让其继承高度（如 tenant-web 的 .rail-drawer-spin）。
 */
export function NavRailDrawer({ open, onClose, width = 300, children }: NavRailDrawerProps) {
  return (
    <Drawer
      placement="left"
      open={open}
      onClose={onClose}
      width={`min(${width}px, 85vw)`}
      closable={false}
      styles={{ body: { padding: 0, overflow: 'hidden' } }}
    >
      {children}
    </Drawer>
  );
}

export type NavRailTriggerProps = {
  /** 按钮文案，与左栏标题同名（如「组织架构」「字典分组」）。 */
  label: string;
  onClick: () => void;
  style?: CSSProperties;
};

/** 窄屏下展开左栏抽屉的入口按钮，五处固定左栏页共用同一形态。 */
export function NavRailTrigger({ label, onClick, style }: NavRailTriggerProps) {
  return (
    <Button size="small" icon={<MenuOutlined />} onClick={onClick} style={style}>
      {label}
    </Button>
  );
}
