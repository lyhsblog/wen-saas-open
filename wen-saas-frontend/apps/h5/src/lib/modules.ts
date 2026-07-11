import { registerModules, type BusinessModuleManifest } from '@wen-saas/module-runtime';

/**
 * h5 app 启用的业务模块清单（H5 业务页动态挂载入口，与 tenant-web `lib/modules.ts` 同构）。
 *
 * 新增业务模块的 H5 页面只需两步：建 `features/<module>/` 页面组件 + 在此登记一条 manifest
 * （`h5Routes`：路径 / 懒加载 / 权限点 / 工作台卡片映射），路由与门禁由 `lib/module-routes.tsx`
 * 统一生成，不再手改 router.tsx 或 WorkspacePage。
 *
 * 约定：
 * - H5 基础壳（4-tab / 通讯录 / 文件等）不走 registry，保持 router.tsx 静态声明。
 * - 菜单与权限点不在 manifest 声明（导航吃后端 capabilities，权限点后端 PermissionRegistry 权威）。
 * - h5Routes.path 与后端 `h5Pages()` 声明对齐：模块 code 前缀、下划线形
 *   （NamespaceValidator 启动期校验；与 PC 路由 `/crm-demo/*` 短横形不同）。
 */
const crmDemoManifest: BusinessModuleManifest = {
  code: 'crm_demo',
  name: '客户管理 Demo',
  version: '0.0.1',
  description: '业务模块 H5 页动态挂载样板。',
  alwaysOn: false,
  h5Routes: [
    {
      path: '/crm_demo/my-customers',
      loader: () =>
        import('../features/crmdemo/MyCustomersPage').then((m) => ({
          default: m.MyCustomersPage,
        })),
      permission: 'crm_demo.customer.view',
      workbenchActionUrl: '/crm-demo/my-customers',
    },
  ],
};

export const moduleRegistry = registerModules([crmDemoManifest]);
