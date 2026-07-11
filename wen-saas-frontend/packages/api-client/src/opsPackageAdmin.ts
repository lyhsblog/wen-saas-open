import type { HttpClient } from './http';
import type { TenantListResponse } from './opsTenant';
import { toWirePageParams } from './pagination';

/**
 * 运营端「套餐包管理」admin CRUD。对应 boot `/api/v1/ops/packages/**`（9 endpoint，v1 2026-05-28）。
 *
 * 注意：与 opsTenant.ts 里的 `opsPackageApi`（租户勾选用的精简套餐目录）用途不同，不要互相替代；
 * 两边 status 都是同一套大写枚举（DRAFT/ACTIVE/INACTIVE/ARCHIVED）。本文件是运营内部维护套餐的完整 CRUD。
 */

/**
 * 套餐状态 4 态。boot 响应体与查询参数已统一为**大写**（OPS-P-032 已对齐，OpenAPI 带 enum 约束）。
 */
export type PackageStatus = 'DRAFT' | 'ACTIVE' | 'INACTIVE' | 'ARCHIVED';

export type ListPackagesQuery = {
  /** 状态过滤；不传 = 全部。 */
  status?: PackageStatus;
  keyword?: string;
};

/** 列表项：含 moduleCount（不含完整 moduleCodes）。 */
export type PackageListItem = {
  code: string;
  name: string;
  description: string | null;
  status: PackageStatus;
  /** null = 永久有效。 */
  defaultTtlDays: number | null;
  isSystem: boolean;
  moduleCount: number;
  createdAt: string;
  updatedAt: string;
};

/** 详情：含完整 moduleCodes（按 code 升序）。 */
export type PackageDetail = {
  code: string;
  name: string;
  description: string | null;
  status: PackageStatus;
  /** null = 永久有效。 */
  defaultTtlDays: number | null;
  isSystem: boolean;
  moduleCodes: string[];
  createdAt: string;
  updatedAt: string;
};

export type PackageCreateRequest = {
  /** 套餐编码，^[A-Z][A-Z0-9_]{1,63}$，创建后不可改。 */
  code: string;
  name: string;
  description?: string | null;
  /** null = 永久有效。 */
  defaultTtlDays?: number | null;
  moduleCodes?: string[] | null;
  /** true = 直接上架，false = 保存为草稿。 */
  publishImmediately: boolean;
};

/** 编辑：各字段 null/省略 = 不改。上架后禁止移除已含模块、base 不可改有效期/模块（服务端兜底）。 */
export type PackageUpdateRequest = {
  name?: string | null;
  description?: string | null;
  defaultTtlDays?: number | null;
  moduleCodes?: string[] | null;
};

/** 可入套餐的业务模块清单项。 */
export type BusinessModuleItem = {
  code: string;
  name: string;
  description: string | null;
  icon: string | null;
};

/** 复制套餐请求（OPS-P-013）：以源套餐 description/defaultTtlDays/moduleCodes 为初值，重填 code+name。 */
export type PackageCopyRequest = {
  /** 新套餐编码，^[A-Z][A-Z0-9_]{1,63}$。 */
  code: string;
  name: string;
};

/** 变更历史项（OPS-P-012）。需看改前/改后 diff 时用 id+createdAt 调 opsOperationLogApi.getDetail。 */
export type PackageHistoryItemView = {
  id: string;
  /** create | update | publish | unpublish | archive | delete | copy */
  action: string;
  operatorId: string | null;
  operatorName: string | null;
  objectDesc: string | null;
  /** 200 成功 / 500 失败。 */
  statusCode: number | null;
  createdAt: string | null;
};

export type PackageHistoryResponse = {
  items: PackageHistoryItemView[];
  total: number;
  page: number;
  pageSize: number;
};

/** 套餐持有租户反查 / 变更历史 的查询参数。 */
export type ListPackageTenantsQuery = {
  page?: number;
  pageSize?: number;
  keyword?: string;
  /** 租户状态：0=暂停 / 1=正常 / 2=已过期。 */
  status?: 0 | 1 | 2;
};

export type ListPackageHistoryQuery = {
  page?: number;
  /** wire 参数名即 size（boot 分页已统一 page/size）。 */
  size?: number;
};

export function opsPackageAdminApi(http: HttpClient) {
  const base = '/api/v1/ops/packages';
  return {
    async listPackages(query?: ListPackagesQuery): Promise<PackageListItem[]> {
      const params = {
        ...(query?.keyword ? { keyword: query.keyword } : {}),
        ...(query?.status ? { status: query.status } : {}),
      };
      const res = await http.get<PackageListItem[]>(base, { params });
      return res.data;
    },
    async getPackage(code: string): Promise<PackageDetail> {
      const res = await http.get<PackageDetail>(`${base}/${encodeURIComponent(code)}`);
      return res.data;
    },
    async createPackage(body: PackageCreateRequest): Promise<PackageDetail> {
      const res = await http.post<PackageDetail>(base, body);
      return res.data;
    },
    async updatePackage(code: string, body: PackageUpdateRequest): Promise<PackageDetail> {
      const res = await http.put<PackageDetail>(`${base}/${encodeURIComponent(code)}`, body);
      return res.data;
    },
    async deletePackage(code: string): Promise<void> {
      await http.delete(`${base}/${encodeURIComponent(code)}`);
    },
    async publish(code: string): Promise<void> {
      await http.put(`${base}/${encodeURIComponent(code)}/publish`, null);
    },
    async unpublish(code: string): Promise<void> {
      await http.put(`${base}/${encodeURIComponent(code)}/unpublish`, null);
    },
    async archive(code: string): Promise<void> {
      await http.put(`${base}/${encodeURIComponent(code)}/archive`, null);
    },
    async listAvailableModules(): Promise<BusinessModuleItem[]> {
      const res = await http.get<BusinessModuleItem[]>(`${base}/modules/available`);
      return res.data;
    },
    /** OPS-P-011 持有租户反查（未撤销）。返回与运营租户列表同形。 */
    async listPackageTenants(
      code: string,
      query?: ListPackageTenantsQuery,
    ): Promise<TenantListResponse> {
      const res = await http.get<TenantListResponse>(
        `${base}/${encodeURIComponent(code)}/tenants`,
        { params: toWirePageParams(query) },
      );
      return res.data;
    },
    /** OPS-P-012 变更历史（时间倒序，异步落库有秒级延迟）。 */
    async listPackageHistory(
      code: string,
      query?: ListPackageHistoryQuery,
    ): Promise<PackageHistoryResponse> {
      const res = await http.get<PackageHistoryResponse>(
        `${base}/${encodeURIComponent(code)}/history`,
        { params: query },
      );
      return res.data;
    },
    /** OPS-P-013 复制套餐，产出草稿（DRAFT）。新码冲突 → 409 CODE_CONFLICT。 */
    async copyPackage(code: string, body: PackageCopyRequest): Promise<PackageDetail> {
      const res = await http.post<PackageDetail>(`${base}/${encodeURIComponent(code)}/copy`, body);
      return res.data;
    },
  };
}
