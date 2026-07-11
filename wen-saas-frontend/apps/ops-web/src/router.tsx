import { type ReactNode } from 'react';
import { createBrowserRouter, Navigate, useParams } from 'react-router';
import { OpsLoginPage } from './features/auth/OpsLoginPage';
import { OpsRequireAuth } from './features/auth/OpsRequireAuth';
import { OpsAppShell } from './components/OpsAppShell';
import { OpsWorkbenchPage } from './features/dashboard/OpsWorkbenchPage';
import { OpsTenantsPage } from './features/tenants/OpsTenantsPage';
import { OnboardResultPage } from './features/tenants/OnboardResultPage';
import { OpsProfilePage } from './features/profile/OpsProfilePage';
import { OpsPackagesPage } from './features/packages/OpsPackagesPage';
import { OpsModulesPage } from './features/modules/OpsModulesPage';
import { OpsRecRolesPage } from './features/recroles/OpsRecRolesPage';
import { OpsNotificationTemplatesPage } from './features/notification-templates/OpsNotificationTemplatesPage';
import { OpsHealthPage } from './features/health/OpsHealthPage';
import { OpsTicketPage } from './features/ticket/OpsTicketPage';
import { OpsCompliancePage } from './features/compliance/OpsCompliancePage';
import { OpsPlatformMonitorPage } from './features/platform/OpsPlatformMonitorPage';
import { OpsSysAdminPage } from './features/sysadmin/OpsSysAdminPage';
import { OpsMenuAdminPage } from './features/sysadmin/OpsMenuAdminPage';

function guarded(node: ReactNode) {
  return (
    <OpsRequireAuth>
      <OpsAppShell>{node}</OpsAppShell>
    </OpsRequireAuth>
  );
}

function TenantDetailRedirect() {
  const { id = '' } = useParams<{ id: string }>();
  return <Navigate to={`/tenants?selected=${encodeURIComponent(id)}&tab=basic`} replace />;
}

export const router = createBrowserRouter([
  { path: '/login', element: <OpsLoginPage /> },
  { path: '/', element: <Navigate to="/workbench" replace /> },
  { path: '/workbench', element: guarded(<OpsWorkbenchPage />) },
  { path: '/tenants', element: guarded(<OpsTenantsPage />) },
  { path: '/tenants/:id', element: guarded(<TenantDetailRedirect />) },
  { path: '/tenants/:id/onboard-result', element: guarded(<OnboardResultPage />) },
  { path: '/modules', element: guarded(<OpsModulesPage />) },
  { path: '/plans', element: guarded(<OpsPackagesPage />) },
  { path: '/recroles', element: guarded(<OpsRecRolesPage />) },
  { path: '/notification-templates', element: guarded(<OpsNotificationTemplatesPage />) },
  { path: '/health', element: guarded(<OpsHealthPage />) },
  { path: '/tickets', element: guarded(<OpsTicketPage />) },
  { path: '/compliance', element: guarded(<OpsCompliancePage />) },
  { path: '/platform', element: guarded(<OpsPlatformMonitorPage />) },
  { path: '/sys', element: guarded(<OpsSysAdminPage />) },
  { path: '/sys/menus', element: guarded(<OpsMenuAdminPage />) },
  { path: '/sys/:section', element: guarded(<OpsSysAdminPage />) },
  { path: '/profile', element: guarded(<OpsProfilePage />) },
  { path: '*', element: <Navigate to="/workbench" replace /> },
]);
