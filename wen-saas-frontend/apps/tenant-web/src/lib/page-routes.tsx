import { lazy, Suspense, type ReactElement } from 'react';
import { matchPath } from 'react-router';
import { PermissionGate, PackageGate } from '@wen-saas/permissions';
import type { ResolvedRoute } from '@wen-saas/module-runtime';
import { RouteLoading } from '../components/RouteLoading';
import { RouteForbidden, RouteModuleLocked, RouteNotFound } from '../components/RouteFallbacks';
import { moduleRegistry } from './modules';

/** 业务模块路由（manifest 非 alwaysOn）需按套餐门控；底座/通用模块（alwaysOn）恒可达。 */
function isPackageGated(route: ResolvedRoute): boolean {
  return moduleRegistry.byCode.get(route.moduleCode)?.alwaysOn !== true;
}

/**
 * 把 registry 输出的 ResolvedRoute 包成页面 element（懒加载 + Suspense + 权限门禁 + 套餐门控）。
 * 不含 AppShell / RequireAuth —— 外壳与守卫由持久布局负责，避免每次导航重挂外壳。
 *
 * <p>套餐门控：未开通业务模块直打 URL 撞「未开通」占位页，与侧栏/工作台
 * 已隐藏入口一致——路由表本身全量注册（element 引用稳定保活），可达性在渲染时按 enabledModules 判。
 */
function toPageElement(route: ResolvedRoute): ReactElement {
  const Comp = route.contribution.component ?? lazy(route.contribution.loader);
  let el: ReactElement = (
    <Suspense fallback={<RouteLoading />}>
      <Comp />
    </Suspense>
  );
  if (route.contribution.permission) {
    el = (
      <PermissionGate permission={route.contribution.permission} fallback={<RouteForbidden />}>
        {el}
      </PermissionGate>
    );
  }
  if (isPackageGated(route)) {
    el = (
      <PackageGate module={route.moduleCode} readonlyFallback fallback={<RouteModuleLocked />}>
        {el}
      </PackageGate>
    );
  }
  return el;
}

/**
 * 全部租户页面路由（相对路径 → 稳定的页面 element 实例）。模块级构建一次：
 * element 引用稳定，保活时同一页签的 element 在重渲染间被 React 复用、不丢挂载。
 */
export const pageRoutes: { path: string; element: ReactElement }[] = moduleRegistry
  .getTenantRoutes()
  .map((r) => ({ path: r.fullPath, element: toPageElement(r) }));

/** 按当前页面路径解析 element（租户在子域名，路径不带租户前缀）；无匹配返回 404 页。 */
export function resolvePageElement(relativePath: string): ReactElement {
  for (const r of pageRoutes) {
    if (matchPath({ path: r.path, end: true }, relativePath)) return r.element;
  }
  return <RouteNotFound />;
}
