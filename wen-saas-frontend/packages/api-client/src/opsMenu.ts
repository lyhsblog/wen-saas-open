import type { HttpClient } from './http';

/** 菜单管理节点（运营「菜单管理」页）。雪花 id 为字符串。 */
export interface MenuAdminNode {
  id: string;
  scope: number;
  nodeKey: string;
  parentKey: string | null;
  /** 1=GROUP 分组 / 2=PAGE 页面 / 3=ACTION 动作。 */
  nodeType: number;
  label: string;
  icon: string | null;
  path: string | null;
  permissionCode: string | null;
  description: string | null;
  sort: number;
  /** 1=SEED 种子（仅可改/启停）/ 2=ADMIN 自建（可删）。 */
  source: number;
  /** 1=启用 / 0=停用。 */
  status: number;
  /** permissionCode 悬空失效（启动校验置位）。 */
  dangling: boolean;
  updatedAt: string | null;
  children: MenuAdminNode[] | null;
}

/** 可授权限码（绑码选码用）。 */
export interface PermissionCodeView {
  code: string;
  name: string;
  description: string | null;
  scope: number;
  dataScoped: boolean;
}

export interface MenuSaveRequest {
  scope: number;
  parentKey: string | null;
  nodeType: number;
  label: string;
  icon: string | null;
  path: string | null;
  permissionCode: string | null;
  description: string | null;
  sort: number | null;
}

export interface MenuUpdateRequest {
  label: string;
  icon: string | null;
  path: string | null;
  permissionCode: string | null;
  description: string | null;
  sort: number | null;
}

export function opsMenuApi(http: HttpClient) {
  const base = '/api/v1/ops/menus';
  return {
    async tree(scope: number): Promise<MenuAdminNode[]> {
      const res = await http.get<MenuAdminNode[]>(base, { params: { scope } });
      return res.data;
    },
    async create(body: MenuSaveRequest): Promise<MenuAdminNode> {
      const res = await http.post<MenuAdminNode>(base, body);
      return res.data;
    },
    async update(id: string, body: MenuUpdateRequest): Promise<MenuAdminNode> {
      const res = await http.put<MenuAdminNode>(`${base}/${id}`, body);
      return res.data;
    },
    async move(id: string, parentKey: string | null, sort: number): Promise<void> {
      await http.put(`${base}/${id}/move`, { parentKey, sort });
    },
    async setStatus(id: string, enabled: boolean): Promise<MenuAdminNode> {
      const res = await http.put<MenuAdminNode>(`${base}/${id}/status`, { enabled });
      return res.data;
    },
    async remove(id: string): Promise<void> {
      await http.delete(`${base}/${id}`);
    },
    async permissionCodes(scope: number): Promise<PermissionCodeView[]> {
      const res = await http.get<PermissionCodeView[]>('/api/v1/ops/permission-codes', {
        params: { scope },
      });
      return res.data;
    },
  };
}
