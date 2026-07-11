import { lazy, Suspense, type ReactElement, type ReactNode } from 'react';
import { Button, DotLoading, ErrorBlock } from 'antd-mobile';
import { useNavigate, type RouteObject } from 'react-router';
import { PermissionGate, useHasModule } from '@wen-saas/permissions';
import type { ResolvedRoute } from '@wen-saas/module-runtime';
import { RequireAuth } from '../features/auth/RequireAuth';
import { moduleRegistry } from './modules';

function RouteLoading() {
  return (
    <div
      style={{
        display: 'flex',
        justifyContent: 'center',
        alignItems: 'center',
        height: '100dvh',
        color: 'var(--brand)',
      }}
    >
      <DotLoading color="primary" />
    </div>
  );
}

/** 全屏占位（无权 / 未开通共用骨架）：说明 + 返回工作台。 */
function RouteBlocked({ title, description }: { title: string; description: string }) {
  const navigate = useNavigate();
  return (
    <div style={{ padding: '64px 24px', textAlign: 'center' }}>
      <ErrorBlock status="default" title={title} description={description} />
      <Button
        color="primary"
        style={{ marginTop: 16 }}
        onClick={() => void navigate('/workspace')}
      >
        返回工作台
      </Button>
    </div>
  );
}

function RouteForbidden() {
  return <RouteBlocked title="无权访问" description="你没有访问该页面的权限，请联系管理员。" />;
}

/** 套餐门禁：模块未开通时渲染占位（与 tenant-web PackageGate 语义一致，UI 换 antd-mobile）。 */
function ModuleGate({ module, children }: { module: string; children: ReactNode }) {
  const enabled = useHasModule(module);
  if (!enabled) {
    return (
      <RouteBlocked title="模块未开通" description="该模块需在套餐中开通后访问，请联系管理员。" />
    );
  }
  return <>{children}</>;
}

/**
 * 把 registry 的 h5Routes 包成页面 element：懒加载 + Suspense + 权限门禁 + 套餐门禁 + 登录守卫。
 * 与 tenant-web `lib/page-routes.tsx` 同构；业务页均为全屏二级页（不带底部 TabBar）。
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
  if (moduleRegistry.byCode.get(route.moduleCode)?.alwaysOn !== true) {
    el = <ModuleGate module={route.moduleCode}>{el}</ModuleGate>;
  }
  return <RequireAuth>{el}</RequireAuth>;
}

/** 业务模块路由表（租户在子域名，路径不带前缀），router.tsx 展开接入。 */
export const moduleRoutes: RouteObject[] = moduleRegistry
  .getH5Routes()
  .map((r) => ({ path: r.fullPath, element: toPageElement(r) }));
