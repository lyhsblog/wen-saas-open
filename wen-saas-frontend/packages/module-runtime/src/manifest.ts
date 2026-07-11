/**
 * 业务模块前端 SPI 类型。与后端 `BusinessModuleDescriptor` 概念对齐：
 * - code/name/version/icon 一一对应
 * - 路由登记（tenantRoutes/h5Routes）声明在模块自身的 manifest.ts
 * - 模块在构建期通过 `registerModules([...])` 加入应用 registry，不做远程动态加载
 *
 * <p>菜单不在 SPI 声明（sys_menu 为 DB 权威），权限点不在 SPI 声明（PermissionRegistry 代码权威），
 * 工作台入口吃 capabilities.menus——故 SPI 不含 MenuContribution / PermissionPoint /
 * WorkbenchCardContribution / tenantMenus / permissionPoints / workbenchCards 这类类型与字段。
 */

import type { ComponentType, LazyExoticComponent } from 'react';

/** 路由贡献。组件以 lazy 形式注入，让 shell 按需 code-split。 */
export type RouteContribution = {
  /** 相对租户根的路径，如 `/system/dictionaries` 或 `/customers/:id`。 */
  path: string;
  /** 懒加载组件；React.lazy 等价 */
  loader: () => Promise<{ default: ComponentType }>;
  /** 已通过 React.lazy 包裹好的组件（可选，与 loader 二选一）。 */
  component?: LazyExoticComponent<ComponentType>;
  /** 访问该路由所需权限点。 */
  permission?: string;
  /**
   * 与本路由对应的工作台卡片 actionUrl（`DashboardCardDef.actionUrl` 按 PC 路由声明，
   * 与 H5 路由可不同形）。H5 工作台据此把命中卡片设为可点并导航到本路由；
   * 不声明则该卡片在 H5 上不可点。仅对 h5Routes 有意义。
   */
  workbenchActionUrl?: string;
};

/** 模块声明要预置的字典分组。后端启动时 upsert，前端 ops 后台可见。 */
export type DictionaryContribution = {
  code: string;
  name: string;
  description?: string;
  supportHierarchy?: boolean;
  seed?: Array<{ code: string; name: string; sortOrder?: number }>;
};

export type NotificationChannel = 'IN_APP' | 'EMAIL' | 'SMS' | 'WX' | 'DING';

export type NotificationTemplateContribution = {
  code: string;
  name: string;
  channels: NotificationChannel[];
  defaultTemplate: string;
};

export type ImportEntityContribution = {
  entityCode: string;
  entityName: string;
  fields: Array<{ field: string; label: string; required?: boolean }>;
};

export type BusinessModuleManifest = {
  code: string;
  name: string;
  version: string;
  icon?: string;
  description?: string;
  /** 必须先注册的模块编码列表；用于 registry topo 排序与缺失检测。 */
  dependsOn?: string[];

  /**
   * 永远启用：菜单 / 卡片不按 `enabledModules` 门禁，对所有租户恒可见。
   *
   * <p>用于"底座框架"（base：工作台 / 字典 / 角色 / 通讯录）与"通用工具"
   * （ticket：工单，后端 `apiPathPrefixes()` 为空、不按套餐拦截）这类不属于任何套餐的模块。
   * 后端 `capabilities.enabledModules` 只含套餐内业务模块（不含 base，也不含通用工具），
   * 故这类模块的 code 永不在 enabledModules 里——置 `alwaysOn` 让其菜单照常渲染。
   * 普通业务模块（如 crm）不设此标志，仍按租户套餐门禁。
   */
  alwaysOn?: boolean;

  tenantRoutes?: RouteContribution[];
  h5Routes?: RouteContribution[];
  dictionaryGroups?: DictionaryContribution[];
  notificationTemplates?: NotificationTemplateContribution[];
  importEntities?: ImportEntityContribution[];
};
