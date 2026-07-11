import {
  permissionCatalogTreeApi,
  tenantRecommendedRoleApi,
  type CatalogModule,
  type CatalogTreeNode,
} from '@wen-saas/api-client';
import { http } from '../../lib/http';

const treeApi = permissionCatalogTreeApi(http);

/**
 * 把后端角色目录树（sys_menu 派生，scope=TENANT）适配成角色页沿用的「模块 → 菜单 → 功能点」三层：
 * 顶层分组 = 模块；其下「有子动作的页」= 菜单（子叶 = 功能点）；其下「单码页」= 单功能点菜单（点名「查看」）。
 * 中文分组从 sys_menu 节点名直出（不再 code 反推），分组全选由角色页三态复选支撑。
 */
function adaptTreeToModules(tree: CatalogTreeNode[]): CatalogModule[] {
  return tree.map((group) => ({
    key: group.key,
    name: group.label,
    base: true,
    menus: (group.children ?? []).map((child) =>
      child.children && child.children.length > 0
        ? {
            key: child.key,
            name: child.label,
            points: child.children.map((leaf) => ({
              code: leaf.code ?? leaf.key,
              name: leaf.label,
              description: leaf.description,
            })),
          }
        : {
            key: child.key,
            name: child.label,
            points: [
              { code: child.code ?? child.key, name: '查看', description: child.description },
            ],
          },
    ),
  }));
}

export const catalogApi = {
  async catalog(): Promise<CatalogModule[]> {
    const tree = await treeApi.base();
    return adaptTreeToModules(tree.tree);
  },
};

export const recommendedRoleApi = tenantRecommendedRoleApi(http);
