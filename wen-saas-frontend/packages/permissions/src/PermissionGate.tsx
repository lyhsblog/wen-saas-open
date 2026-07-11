import { type ReactNode } from 'react';
import { useHasPermission } from './capabilities';

export type PermissionGateProps = {
  permission: string;
  /** 命中失败时渲染的内容，默认不渲染（按钮直接消失）。 */
  fallback?: ReactNode;
  children: ReactNode;
};

/**
 * 权限点开关：渲染前判断 capabilities 中是否包含 `permission`。
 * - capabilities 未加载（caps == null）→ 视为无权限，不渲染（避免按钮先亮再消失）
 * - tenant_owner=true 的用户恒命中
 */
export function PermissionGate({ permission, fallback = null, children }: PermissionGateProps) {
  const allowed = useHasPermission(permission);
  return <>{allowed ? children : fallback}</>;
}
