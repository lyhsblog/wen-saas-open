import type {
  RoleView,
  RoleDetail,
  RoleCreateRequest,
  RoleUpdateRequest,
  RolePermissionsUpdateRequest,
  MemberRef,
  MemberIdsRequest,
} from '@wen-saas/domain';
import type { TenantDataScope } from './enums/dataScope';
import type { HttpClient } from './http';

export interface RoleDataScopeView {
  defaultScope: TenantDataScope;
  actionOverrides: Record<string, TenantDataScope>;
}

export interface SaveDataScopeCmd {
  defaultScope: TenantDataScope;
  actionOverrides: Record<string, TenantDataScope>;
}

export interface DataPermissionActionDef {
  code: string;
  name: string;
}

export function roleAdminApi(http: HttpClient) {
  const base = '/api/v1/base/roles';
  return {
    async listRoles(): Promise<RoleView[]> {
      const res = await http.get<RoleView[]>(base);
      return res.data;
    },
    async getRole(roleId: string): Promise<RoleDetail> {
      const res = await http.get<RoleDetail>(`${base}/${encodeURIComponent(roleId)}`);
      return res.data;
    },
    async createRole(body: RoleCreateRequest): Promise<RoleDetail> {
      const res = await http.post<RoleDetail>(base, body);
      return res.data;
    },
    async updateRole(roleId: string, body: RoleUpdateRequest): Promise<RoleDetail> {
      const res = await http.put<RoleDetail>(`${base}/${encodeURIComponent(roleId)}`, body);
      return res.data;
    },
    async deleteRole(roleId: string): Promise<void> {
      await http.delete(`${base}/${encodeURIComponent(roleId)}`);
    },
    async updateRolePermissions(
      roleId: string,
      body: RolePermissionsUpdateRequest,
    ): Promise<RoleDetail> {
      const res = await http.put<RoleDetail>(
        `${base}/${encodeURIComponent(roleId)}/permissions`,
        body,
      );
      return res.data;
    },
    async setRoleStatus(roleId: string, enabled: boolean): Promise<RoleDetail> {
      const res = await http.put<RoleDetail>(`${base}/${encodeURIComponent(roleId)}/status`, null, {
        params: { enabled },
      });
      return res.data;
    },
    async listRoleMembers(roleId: string): Promise<MemberRef[]> {
      const res = await http.get<MemberRef[]>(`${base}/${encodeURIComponent(roleId)}/members`);
      return res.data;
    },
    async addRoleMembers(roleId: string, body: MemberIdsRequest): Promise<RoleDetail> {
      const res = await http.post<RoleDetail>(
        `${base}/${encodeURIComponent(roleId)}/members`,
        body,
      );
      return res.data;
    },
    async removeRoleMembers(roleId: string, body: MemberIdsRequest): Promise<void> {
      await http.delete(`${base}/${encodeURIComponent(roleId)}/members`, { data: body });
    },
    async getRoleDataScope(roleId: string): Promise<RoleDataScopeView> {
      const res = await http.get<RoleDataScopeView>(
        `${base}/${encodeURIComponent(roleId)}/data-scope`,
      );
      return res.data;
    },
    async saveRoleDataScope(roleId: string, body: SaveDataScopeCmd): Promise<void> {
      await http.put(`${base}/${encodeURIComponent(roleId)}/data-scope`, body);
    },
    async listDataPermissionActions(): Promise<DataPermissionActionDef[]> {
      const res = await http.get<DataPermissionActionDef[]>(`${base}/data-permission-actions`);
      return res.data;
    },
  };
}
