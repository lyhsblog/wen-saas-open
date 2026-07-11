import { DevEntryPage } from '../features/dev/DevEntryPage';
import { RequireAuth } from '../features/auth/RequireAuth';
import { AppShell } from './AppShell';
import { useTenantKey } from '../lib/tenant-path';

/**
 * 根与深层路径的分流：租户由子域名区分（{tenantKey}.基础域），路径不再带 tenantKey 段。
 * 有租户子域 → 登录守卫 + 持久外壳（AppShell 内 KeepAlivePanes 依当前路径解析页面）；
 * 裸域（无租户）→ 本地调试入口。
 */
export function TenantRootGate() {
  const tenantKey = useTenantKey();
  if (!tenantKey) return <DevEntryPage />;
  return (
    <RequireAuth>
      <AppShell />
    </RequireAuth>
  );
}
