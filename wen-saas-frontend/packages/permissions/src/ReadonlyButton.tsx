import { Button, Tooltip, type ButtonProps } from 'antd';
import { useIsReadonly } from './capabilities';

export type ReadonlyButtonProps = ButtonProps & {
  /** 套餐只读态下悬停提示文案。 */
  readonlyTooltip?: string;
};

/**
 * 写操作按钮：套餐到期 / 欠费（`packageReadonly`）时强制 `disabled` 并套 Tooltip 解释，
 * 非只读态行为与原生 antd `Button` 完全一致。
 *
 * 与 `PermissionGate` 配合形成完整门控：权限不足 → 隐藏（PermissionGate）；套餐只读 → 灰化（本组件）。
 * 二者可叠加在同一按钮（外 PermissionGate 包裹、内用 ReadonlyButton）。
 *
 * 非 Button 写控件（Upload / Switch / Popconfirm 等）请直接复用 `useIsReadonly()`
 * 把布尔 OR 进各自的 `disabled`，无需改成本组件。
 */
export function ReadonlyButton({
  readonlyTooltip = '当前套餐已到期，暂不可操作',
  disabled,
  ...rest
}: ReadonlyButtonProps) {
  const readonly = useIsReadonly();
  const btn = <Button {...rest} disabled={disabled || readonly} />;
  return readonly ? <Tooltip title={readonlyTooltip}>{btn}</Tooltip> : btn;
}
