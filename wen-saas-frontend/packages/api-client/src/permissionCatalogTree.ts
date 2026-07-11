import type { HttpClient } from './http';

/**
 * 角色权限目录树（后端 sys_menu 派生）。
 * 容器节点 code 为 null（仅聚合三态、不可授）；叶子节点 code 非空（勾它=授予该码）。
 */
export interface CatalogTreeNode {
  key: string;
  label: string;
  /** 可授权限码；容器节点为 null。 */
  code: string | null;
  description: string | null;
  /** 子节点；叶子为 null。 */
  children: CatalogTreeNode[] | null;
}

/** 孤儿码（后端可授但无菜单引用，提示绑定用，不进可勾树）。 */
export interface UnboundCode {
  code: string;
  name: string;
  description: string | null;
}

export interface CatalogTree {
  tree: CatalogTreeNode[];
  unbound: UnboundCode[];
}

/**
 * 角色权限目录树 API。两端共用同一棵树结构：
 * - `base()` 租户端 `/api/v1/base/permission-catalog`（scope=TENANT）
 * - `ops()` 运营端 `/api/v1/ops/permission-catalog`（scope=OPS）
 */
export function permissionCatalogTreeApi(http: HttpClient) {
  return {
    async base(): Promise<CatalogTree> {
      const res = await http.get<CatalogTree>('/api/v1/base/permission-catalog');
      return res.data;
    },
    async ops(): Promise<CatalogTree> {
      const res = await http.get<CatalogTree>('/api/v1/ops/permission-catalog');
      return res.data;
    },
  };
}
