import type { ReactNode } from 'react';
import { Button, Dropdown, Tooltip } from 'antd';
import type { MenuProps } from 'antd';
import { EllipsisOutlined } from '@ant-design/icons';

/**
 * 表格行内操作规范（B14 口径）：
 * - 行内文字操作 ≤3 个：全部平铺（RowTextAction / primary）。
 * - ≥4 个：保留最高频 2–3 个平铺，其余收进「⋯」Dropdown（more）。
 * - 删除等危险操作一律收纳进「⋯」、置底、danger 红字，上方自动加分隔线。
 * - 行内有 mutation 在途时对应项 pending（loading + 置灰），防重复触发。
 */

export type RowTextActionProps = {
  onClick: () => void;
  /** 危险操作红字（平铺形态仅特殊场景使用，常规危险操作应收进 more） */
  danger?: boolean;
  /** 请求在途：loading + 禁用 */
  pending?: boolean;
  disabled?: boolean;
  /** 悬浮提示（常用于解释禁用原因） */
  tooltip?: ReactNode;
  children: ReactNode;
};

/** 行内文字操作：AntD link 按钮（padding 0），带原生 button 语义与键盘可达性。 */
export function RowTextAction({
  onClick,
  danger,
  pending,
  disabled,
  tooltip,
  children,
}: RowTextActionProps) {
  const isDisabled = !!disabled || !!pending;
  const btn = (
    <Button
      type="link"
      size="small"
      danger={!!danger}
      loading={!!pending}
      disabled={isDisabled}
      onClick={onClick}
      style={{ padding: 0 }}
    >
      {children}
    </Button>
  );
  if (!tooltip) return btn;
  // 禁用按钮不触发鼠标事件，包一层 span 让 Tooltip 仍可弹出
  return (
    <Tooltip title={tooltip}>
      {isDisabled ? (
        <span style={{ display: 'inline-flex', cursor: 'not-allowed' }}>{btn}</span>
      ) : (
        btn
      )}
    </Tooltip>
  );
}

export type RowActionDef = {
  key: string;
  label: ReactNode;
  onClick: () => void;
  danger?: boolean;
  /** 请求在途：平铺形态 loading + 禁用；收纳形态置灰 */
  pending?: boolean;
  disabled?: boolean;
  tooltip?: ReactNode;
};

export type RowActionsBarProps = {
  /** 平铺的高频操作，≤3 个 */
  primary: RowActionDef[];
  /** 收进「⋯」Dropdown 的低频 / 危险操作；danger 项自动置底并加分隔线 */
  more?: RowActionDef[];
};

function toMenuItem(a: RowActionDef): NonNullable<MenuProps['items']>[number] {
  // 菜单容器按内容收缩，两字项会被折成竖排，label 统一禁换行
  const label = <span style={{ whiteSpace: 'nowrap' }}>{a.label}</span>;
  return {
    key: a.key,
    danger: !!a.danger,
    disabled: !!a.disabled || !!a.pending,
    label: a.tooltip ? <Tooltip title={a.tooltip}>{label}</Tooltip> : label,
  };
}

/** 行内操作条：primary 平铺 + more 收纳（触发器为带 aria-label 的「⋯」图标按钮）。 */
export function RowActionsBar({ primary, more }: RowActionsBarProps) {
  const moreActions = more ?? [];
  // danger 项统一置底；普通项与 danger 项之间加分隔线
  const normal = moreActions.filter((a) => !a.danger);
  const dangerous = moreActions.filter((a) => a.danger);
  const items: MenuProps['items'] = [
    ...normal.map(toMenuItem),
    ...(normal.length > 0 && dangerous.length > 0 ? [{ type: 'divider' as const }] : []),
    ...dangerous.map(toMenuItem),
  ];

  const onMenuClick: MenuProps['onClick'] = ({ key }) => {
    const hit = moreActions.find((a) => a.key === key);
    if (hit && !hit.disabled && !hit.pending) hit.onClick();
  };

  return (
    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
      {primary.map((a) => (
        <RowTextAction
          key={a.key}
          onClick={a.onClick}
          danger={!!a.danger}
          pending={!!a.pending}
          disabled={!!a.disabled}
          tooltip={a.tooltip}
        >
          {a.label}
        </RowTextAction>
      ))}
      {moreActions.length > 0 && (
        <Dropdown menu={{ items, onClick: onMenuClick }} trigger={['click']}>
          <Button type="text" size="small" aria-label="更多操作" icon={<EllipsisOutlined />} />
        </Dropdown>
      )}
    </span>
  );
}
