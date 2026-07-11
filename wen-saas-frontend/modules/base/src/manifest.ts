import type { BusinessModuleManifest } from '@wen-saas/module-runtime';

/**
 * 底座模块占位 manifest（仅承载模块身份：code/name/version/description）。
 * 路由由入口 app 在 registerModules 时补 tenantRoutes；菜单与权限点不在 SPI 声明
 * ——导航树吃后端 capabilities.menus（sys_menu 派生）、权限点吃 PermissionRegistry。
 * 入口 app 用 `registerModules([baseManifest, ...其它])` 注册即可。
 */
export const baseManifest: BusinessModuleManifest = {
  code: 'base',
  name: '底座',
  version: '0.0.1',
  description: '提供工作台、字典管理等通用能力，所有业务模块默认依赖。',
};
