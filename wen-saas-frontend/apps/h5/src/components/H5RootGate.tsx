import { DevEntryPage } from '../features/dev/DevEntryPage';
import { RequireAuth } from '../features/auth/RequireAuth';
import { AppLayout } from './AppLayout';
import { useTenantKey } from '../lib/tenant-path';

/**
 * 根分流：租户由子域名区分（{tenantKey}.基础域），路径不再带 tenantKey 段。
 * 有租户子域 → 登录守卫 + 4-tab 主壳（Outlet 承载 tab 页）；裸域（无租户）→ 本地调试入口。
 */
export function H5RootGate() {
  const tenantKey = useTenantKey();
  if (!tenantKey) return <DevEntryPage />;
  return (
    <RequireAuth>
      <AppLayout />
    </RequireAuth>
  );
}
