/**
 * 角色页沿用的「模块 → 菜单 → 功能点」三层视图类型。
 *
 * 数据源已统一为 sys_menu 派生的 catalogTree（{@link permissionCatalogTree}）；
 * 租户角色页经本地适配器把 catalogTree 映射成这套形状渲染。仅保留类型，无独立端点。
 */
export interface CatalogPoint {
  code: string;
  name: string;
  description: string | null;
}

export interface CatalogMenu {
  key: string;
  name: string;
  points: CatalogPoint[];
}

export interface CatalogModule {
  key: string;
  name: string;
  base: boolean;
  menus: CatalogMenu[];
}
