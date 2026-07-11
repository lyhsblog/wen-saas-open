import { useNavigate } from 'react-router';
import { Result } from 'antd';
import { Forbidden403Page, NotFound404Page } from '@wen-saas/shared-ui';
import { WaBtn } from './wa';
import { tenantPath, useTenantKey } from '../lib/tenant-path';

/** 路由级 403：PermissionGate 失败时撞墙的页面。 */
export function RouteForbidden() {
  const navigate = useNavigate();
  const tenantKey = useTenantKey();
  return (
    <Forbidden403Page
      onBackHome={() => {
        void navigate(tenantPath(tenantKey, '/workbench'), { replace: true });
      }}
    />
  );
}

/** 路由级套餐门控：直打未开通业务模块 URL 时撞墙（与侧栏/工作台已隐藏入口一致）。 */
export function RouteModuleLocked() {
  const navigate = useNavigate();
  const tenantKey = useTenantKey();
  return (
    <Result
      status="warning"
      title="该模块未在套餐中开通"
      subTitle="需在套餐中开通后访问；如部分模块到期，续期后将恢复。请联系管理员。"
      extra={
        <WaBtn kind="primary" onClick={() => void navigate(tenantPath(tenantKey, '/workbench'))}>
          返回工作台
        </WaBtn>
      }
    />
  );
}

/** 路由级 404：匹配不到任何 registry route 的兜底。 */
export function RouteNotFound() {
  const navigate = useNavigate();
  const tenantKey = useTenantKey();
  return (
    <NotFound404Page
      onBackHome={() => {
        if (tenantKey) {
          void navigate(tenantPath(tenantKey, '/workbench'), { replace: true });
        } else {
          void navigate('/', { replace: true });
        }
      }}
    />
  );
}
