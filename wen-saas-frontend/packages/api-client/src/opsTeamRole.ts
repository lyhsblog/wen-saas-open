import type { HttpClient } from './http';

/**
 * 运营端「系统管理 · 角色权限」admin CRUD。对应 boot `/api/v1/ops/team/roles/**`（9 endpoint）。
 *
 * 运营 RBAC 复用 sys_role（tenant_id=0，权限点为 `ops.*`）。2 预置角色
 * OPS_SUPER_ADMIN / OPS_CSM（systemPreset=true，禁删、code 只读）。
 * 数据范围运营域只有 ALL / OWNED（CSM 仅看 csm_user_id=self 的租户）。
 *
 * 权限点 / 成员均为「全量替换」语义。雪花 Long（id / userIds）JSON 为字符串。
 */

/** 运营域数据范围：ALL=全部 / OWNED=仅本人负责的租户。 */
export type OpsRoleDataScope = 'ALL' | 'OWNED';

/** 列表项：含 memberCount + permissionCount（计算字段）+ systemPreset。预置在前 + 自定义在后。 */
export type OpsRoleListItem = {
  /** 雪花 Long，JSON 为字符串。 */
  id: string;
  code: string;
  name: string;
  description: string | null;
  dataScope: OpsRoleDataScope;
  status: number;
  memberCount: number;
  permissionCount: number;
  systemPreset: boolean;
  createdAt: string;
};

/** 详情：含完整 permissions（ops.* 权限点 code 列表）。 */
export type OpsRoleDetail = {
  /** 雪花 Long，JSON 为字符串。 */
  id: string;
  code: string;
  name: string;
  description: string | null;
  dataScope: OpsRoleDataScope;
  status: number;
  memberCount: number;
  systemPreset: boolean;
  permissions: string[];
  createdAt: string;
  updatedAt: string;
};

export type OpsRoleCreateRequest = {
  /** 运营域唯一，^[A-Z][A-Z0-9_]{1,63}$。 */
  code: string;
  name: string;
  description?: string | null;
  dataScope: OpsRoleDataScope;
};

/** 编辑基本信息（不含权限 / 成员）；各字段 null/省略 = 不改。预置角色 code 不可改。 */
export type OpsRoleUpdateRequest = {
  name?: string | null;
  description?: string | null;
  dataScope?: OpsRoleDataScope | null;
};

export type OpsRolePermissionsUpdateRequest = {
  /** ops.* 权限点全集，替换式。 */
  permissionCodes: string[];
};

export type OpsRoleMembersRequest = {
  /** 该角色成员 userId 全集（雪花 Long → 字符串），替换式。 */
  userIds: string[];
};

/** 角色成员精简引用。 */
export type OpsRoleMemberRef = {
  /** 雪花 Long，JSON 为字符串。 */
  userId: string;
  username: string;
  realName: string;
};

export function opsTeamRoleApi(http: HttpClient) {
  const base = '/api/v1/ops/team/roles';
  return {
    async listRoles(): Promise<OpsRoleListItem[]> {
      const res = await http.get<OpsRoleListItem[]>(base);
      return res.data;
    },
    async getRole(roleId: string): Promise<OpsRoleDetail> {
      const res = await http.get<OpsRoleDetail>(`${base}/${roleId}`);
      return res.data;
    },
    async createRole(body: OpsRoleCreateRequest): Promise<OpsRoleDetail> {
      const res = await http.post<OpsRoleDetail>(base, body);
      return res.data;
    },
    async updateRole(roleId: string, body: OpsRoleUpdateRequest): Promise<OpsRoleDetail> {
      const res = await http.put<OpsRoleDetail>(`${base}/${roleId}`, body);
      return res.data;
    },
    /** 删除（预置角色禁止；要求无成员，否则 409）。 */
    async deleteRole(roleId: string): Promise<void> {
      await http.delete(`${base}/${roleId}`);
    },
    /** 全量替换权限点。 */
    async updatePermissions(
      roleId: string,
      body: OpsRolePermissionsUpdateRequest,
    ): Promise<OpsRoleDetail> {
      const res = await http.put<OpsRoleDetail>(`${base}/${roleId}/permissions`, body);
      return res.data;
    },
    async listRoleMembers(roleId: string): Promise<OpsRoleMemberRef[]> {
      const res = await http.get<OpsRoleMemberRef[]>(`${base}/${roleId}/members`);
      return res.data;
    },
    /** 全量替换成员。 */
    async assignMembers(roleId: string, body: OpsRoleMembersRequest): Promise<void> {
      await http.put(`${base}/${roleId}/members`, body);
    },
  };
}
