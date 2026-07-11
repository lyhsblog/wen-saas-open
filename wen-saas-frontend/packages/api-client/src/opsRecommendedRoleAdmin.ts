import type { HttpClient } from './http';
import type { CatalogTree } from './permissionCatalogTree';

/**
 * 运营端「推荐角色管理」admin CRUD。对应 boot `/api/v1/ops/recommended-roles/**`（10 endpoint）。
 *
 * 与套餐包（opsPackageAdmin.ts）同构，但有三处差异：
 *  1. 标识符是 `id`（雪花 Long，JSON 序列化为**字符串**以避免 JS 精度丢失；套餐用 code）；
 *  2. 状态是 3 态 DRAFT/PUBLISHED/INACTIVE（套餐 4 态）；
 *  3. 关联的是「套餐包多选 + 跨业务模块权限点」（套餐关联的是业务模块）。
 *
 * 权限点排除 `ops.*`（推荐角色是给租户用的角色，不含运营权限）；权限树按 group 分组返回。
 */

/** 推荐角色模板状态 3 态。boot 响应与查询参数统一为大写。 */
export type RecRoleStatus = 'DRAFT' | 'PUBLISHED' | 'INACTIVE';

/**
 * 默认数据范围（租户角色语义，区别于运营域只有 ALL/OWNED）：
 *  1 = 全部数据 / 2 = 本部门含下属 / 3 = 仅本人。
 */
export type RecRoleDataScope = 1 | 2 | 3;

export type ListRecRolesQuery = {
  /** 状态过滤；不传 = 全部。 */
  status?: RecRoleStatus;
  keyword?: string;
};

/** 列表项：含 permissionCount + activatedTenantCount（计算字段）。 */
export type RecRoleListItem = {
  /** 雪花 Long，JSON 为字符串。 */
  id: string;
  name: string;
  description: string | null;
  status: RecRoleStatus;
  dataScope: RecRoleDataScope;
  packageCodes: string[];
  permissionCount: number;
  activatedTenantCount: number;
  createdAt: string;
  updatedAt: string;
};

/** 关联套餐包富信息（详情专用）：每包已勾选租户数（OPS-R-021）+ 归档标记（OPS-R-023）。 */
export type AssociatedPackage = {
  code: string;
  /** 套餐名（套餐已删则降级为 code）。 */
  name: string;
  isSystem: boolean;
  /** 套餐当前状态（DRAFT/ACTIVE/INACTIVE/ARCHIVED；套餐已删为 null）。 */
  status: string | null;
  archived: boolean;
  /** 已勾选该套餐的租户数。 */
  tenantCount: number;
};

/**
 * 详情：含完整 packageCodes + permissionCodes + 关联套餐包富信息 + 失效权限点 + 全归档标记。
 *
 * packageCodes 仍保留供编辑表单回填；packages 用于详情展示（带 tenantCount/archived）。
 */
export type RecRoleDetail = {
  /** 雪花 Long，JSON 为字符串。 */
  id: string;
  name: string;
  description: string | null;
  status: RecRoleStatus;
  dataScope: RecRoleDataScope;
  packageCodes: string[];
  permissionCodes: string[];
  /** 关联套餐包富信息（OPS-R-021/023）。 */
  packages: AssociatedPackage[];
  /** 已落库但当前未注册的权限点（OPS-R-022 部分权限失效）。 */
  invalidPermissionCodes: string[];
  /** 关联套餐包是否全部已归档（OPS-R-023，读时已惰性转下架）。 */
  allPackagesArchived: boolean;
  activatedTenantCount: number;
  createdAt: string;
  updatedAt: string;
};

/** 已启用租户项（OPS-R-020）。tenantId/createdRoleId 是雪花 Long → 字符串。 */
export type RecRoleActivationItem = {
  tenantId: string;
  tenantName: string | null;
  tenantCode: string | null;
  csmName: string | null;
  createdRoleId: string | null;
  /** 租户内快照角色名（可空：角色被租户删除时为陈旧记录）。 */
  roleName: string | null;
  /** 该快照角色当前已分配成员数（角色已删或未分配时为 0）。 */
  memberCount: number;
  activatedAt: string;
};

/** 已启用租户分页（OPS-R-020）。 */
export type RecRoleActivationPage = {
  items: RecRoleActivationItem[];
  total: number;
  page: number;
  pageSize: number;
};

export type RecRoleCreateRequest = {
  name: string;
  description?: string | null;
  /** 1=全部 / 2=本部门含下属 / 3=仅本人。 */
  dataScope: RecRoleDataScope;
  /** 至少 1 个；校验存在于 sys_package。 */
  packageCodes: string[];
  /** 跨业务模块权限点；不含 ops.*。 */
  permissionCodes?: string[] | null;
  /** true = 直接发布，false = 保存为草稿。 */
  publishImmediately: boolean;
};

/** 编辑：各字段 null/省略 = 不改。已发布态禁止移除已含套餐包/权限点（服务端兜底）。 */
export type RecRoleUpdateRequest = {
  name?: string | null;
  description?: string | null;
  dataScope?: RecRoleDataScope | null;
  packageCodes?: string[] | null;
  permissionCodes?: string[] | null;
};

/** 复制请求：以源模板字段为初值，重填 name，产出草稿。 */
export type RecRoleCopyRequest = {
  name: string;
};

/** 可关联的套餐包（已发布/草稿，排除已归档）。 */
export type PackageRef = {
  code: string;
  name: string;
  /** 套餐状态（大写字符串）。 */
  status: string;
  isSystem: boolean;
};

export function opsRecommendedRoleAdminApi(http: HttpClient) {
  const base = '/api/v1/ops/recommended-roles';
  return {
    async listTemplates(query?: ListRecRolesQuery): Promise<RecRoleListItem[]> {
      const params = {
        ...(query?.keyword ? { keyword: query.keyword } : {}),
        ...(query?.status ? { status: query.status } : {}),
      };
      const res = await http.get<RecRoleListItem[]>(base, { params });
      return res.data;
    },
    async getTemplate(id: string): Promise<RecRoleDetail> {
      const res = await http.get<RecRoleDetail>(`${base}/${id}`);
      return res.data;
    },
    async createTemplate(body: RecRoleCreateRequest): Promise<RecRoleDetail> {
      const res = await http.post<RecRoleDetail>(base, body);
      return res.data;
    },
    async updateTemplate(id: string, body: RecRoleUpdateRequest): Promise<RecRoleDetail> {
      const res = await http.put<RecRoleDetail>(`${base}/${id}`, body);
      return res.data;
    },
    async deleteTemplate(id: string): Promise<void> {
      await http.delete(`${base}/${id}`);
    },
    async publish(id: string): Promise<void> {
      await http.put(`${base}/${id}/publish`, null);
    },
    async unpublish(id: string): Promise<void> {
      await http.put(`${base}/${id}/unpublish`, null);
    },
    async copyTemplate(id: string, body: RecRoleCopyRequest): Promise<RecRoleDetail> {
      const res = await http.post<RecRoleDetail>(`${base}/${id}/copy`, body);
      return res.data;
    },
    /** 权限点目录树（sys_menu 派生的 catalogTree，与角色页同源；模板跨租户不做套餐门控）。 */
    async listPermissionCatalog(): Promise<CatalogTree> {
      const res = await http.get<CatalogTree>(`${base}/permission-catalog`);
      return res.data;
    },
    /** 可关联的套餐包（排除已归档）。 */
    async listAvailablePackages(): Promise<PackageRef[]> {
      const res = await http.get<PackageRef[]>(`${base}/available-packages`);
      return res.data;
    },
    /**
     * 已启用租户列表（OPS-R-020，分页，启用时间倒序）。
     * 数据由租户端「启用推荐角色 → 生成快照角色」写入，尚无租户启用时恒为空页。
     */
    async listActivations(
      id: string,
      query?: { page?: number; pageSize?: number },
    ): Promise<RecRoleActivationPage> {
      // wire 参数名为 size（boot 分页已统一 page/size）。
      const params = {
        ...(query?.page ? { page: query.page } : {}),
        ...(query?.pageSize ? { size: query.pageSize } : {}),
      };
      const res = await http.get<RecRoleActivationPage>(`${base}/${id}/activations`, { params });
      // total 是后端 long（全局 Jackson 序列化为字符串），统一转回 number 供分页计算。
      return { ...res.data, total: Number(res.data.total) };
    },
  };
}
