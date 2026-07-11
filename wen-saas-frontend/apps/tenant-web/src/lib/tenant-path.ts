/**
 * 租户标识来自子域名（白标）：租户端形如 {tenantKey}.{基础域}，基础域由
 * VITE_TENANT_BASE_DOMAIN 注入（线上 saas.xuwen.site / 本地 localhost）。
 * 裸域（无子域）视为无租户，落到入口/选择页。路径里不再含 tenantKey。
 */
export function currentTenantKeyFromLocation(
  host: string = window.location.hostname,
): string | null {
  const base = import.meta.env.VITE_TENANT_BASE_DOMAIN as string | undefined;
  if (!base || host === base || !host.endsWith(`.${base}`)) return null;
  const sub = host.slice(0, host.length - base.length - 1);
  // 仅接受单段子域（如 acme）；多段或空视为无租户。
  return sub && !sub.includes('.') ? sub : null;
}

/**
 * 租户内导航路径。租户标识已在子域名，路径不再带前缀，故直接规整为绝对子路径。
 * 保留双参签名以便调用点无感（第一个参数不再参与拼接）。
 */
export function tenantPath(_tenantKey: string, sub: string): string {
  return sub.startsWith('/') ? sub : `/${sub}`;
}

/** 组件内获取当前租户 key（读子域名，租户内恒定）；无租户时返回空串。 */
export function useTenantKey(): string {
  return currentTenantKeyFromLocation() ?? '';
}
