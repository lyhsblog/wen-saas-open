import type { HttpClient } from './http';
import { toWirePageParams } from './pagination';

export type ActivationStatus =
  | 'pending_activation'
  | 'activated'
  | 'paused'
  | 'expired'
  | 'unknown';

/** 列表排序键。boot 端 `sort=lastActiveAt:desc|createdAt:desc|nearestExpireAt:asc`。 */
export type TenantSortKey = 'lastActiveAt' | 'createdAt' | 'nearestExpireAt';
export type TenantSortDir = 'asc' | 'desc';
export type TenantSort = `${TenantSortKey}:${TenantSortDir}`;

export type CreateTenantRequest = {
  tenantName: string;
  tenantCode: string;
  tenantRemark?: string | null;
  adminUsername: string;
  adminRealName: string;
  adminMobile: string;
  adminEmail: string;
  /** 客户经理（运营账号 id，非必填）。 */
  csmUserId?: string | null;
  /** 成员数上限（null=不限制）。 */
  memberCap?: number | null;
  /** 存储上限字节数（null=不限制）。 */
  storageCap?: number | null;
  /** 租户级有效期 ISO 时间（null=永久）。 */
  expireAt?: string | null;
  /** IANA 时区，如 Asia/Shanghai。 */
  timezone?: string | null;
  /** 公司全称。 */
  fullName?: string | null;
  /** 统一社会信用代码。 */
  uscc?: string | null;
  industry?: string | null;
  scale?: string | null;
  region?: string | null;
  tags?: string[] | null;
  seatAddonQty?: number | null;
  seatAddonRemark?: string | null;
  packageCodes: string[];
};

export type CreatedTenantView = {
  tenantId: string;
  tenantCode: string;
  tenantNo: string;
  adminUsername: string;
  resetToken: string;
  resetExpiresAt: string;
};

export type ListTenantsQuery = {
  page?: number;
  pageSize?: number;
  keyword?: string;
  status?: 0 | 1 | 2;
  expiringWithinDays?: number;
  hasSeatAddon?: boolean;
  /** 多值套餐筛选（OPS-T-019）。 */
  packageCode?: string[];
  /** 排序（OPS-T-017）。默认 lastActiveAt:desc。 */
  sort?: TenantSort;
};

export type TenantListItemView = {
  id: string;
  code: string;
  name: string;
  tenantNo: string | null;
  adminUsername: string | null;
  /** 客户经理 id / 显示名（OPS-T-002/019）。 */
  csmUserId: string | null;
  csmName: string | null;
  status: number;
  activationStatus: ActivationStatus;
  createdAt: string | null;
  /** 最后活跃时间（OPS-T-022），来自成员 last_login_at MAX。 */
  lastActiveAt: string | null;
  /** active 套餐数 + 套餐 code 列表（OPS-T-020）。 */
  planCount: number;
  planCodes: string[];
  /** active 套餐中最早 expiresAt（OPS-T-021）。 */
  nearestExpireAt: string | null;
  /** 累计加购席位（OPS-T-023）。 */
  seatAddonTotal: number;
};

export type TenantListResponse = {
  items: TenantListItemView[];
  total: number;
  page: number;
  pageSize: number;
};

// ===================== detail =====================

export type OpsTenantBasicView = {
  id: string;
  code: string;
  name: string;
  tenantNo: string | null;
  remark: string | null;
  /** 客户经理（OPS-T-002）。 */
  csmUserId: string | null;
  csmName: string | null;
  /** 配额与到期（OPS-T-005）。 */
  memberCap: number | null;
  storageCap: number | null;
  expireAt: string | null;
  timezone: string | null;
  /** 公司扩展信息（OPS-T-001）。 */
  fullName: string | null;
  uscc: string | null;
  industry: string | null;
  scale: string | null;
  region: string | null;
  tags: string[] | null;
  status: number;
  activationStatus: ActivationStatus;
  createdAt: string | null;
  lastActiveAt: string | null;
};

export type OpsMainAdminView = {
  userId: string;
  username: string;
  realName: string | null;
  mobile: string | null;
  email: string | null;
  /** 0 = 未激活 / 1 = 正常 */
  status: number;
};

/** 与 /api/v1/me/capabilities 中的 PackageView 同结构。 */
export type OpsPlanView = {
  code: string;
  name: string;
  /** null = 永久有效。 */
  expiresAt: string | null;
  /** true = 已过期且无 active 套餐覆盖。 */
  readonly: boolean;
};

export type OpsSeatAddonHistoryItem = {
  id: string;
  quantity: number;
  remark: string | null;
  operatorId: string | null;
  operatorName: string | null;
  createdAt: string | null;
};

export type OpsSeatAddonView = {
  totalQuantity: number;
  history: OpsSeatAddonHistoryItem[];
};

export type OpsTenantDetailView = {
  tenant: OpsTenantBasicView;
  /** mainAdmin 可能为 null（理论上每个租户都应有主管理员，防御性 nullable）。 */
  mainAdmin: OpsMainAdminView | null;
  plans: OpsPlanView[];
  seatAddons: OpsSeatAddonView;
};

// ===================== reissue reset link =====================

export type ReissuedResetLinkView = {
  resetToken: string;
  resetExpiresAt: string;
  adminUsername: string;
};

// ===================== seat addon =====================

export type AddSeatAddonRequest = {
  quantity: number;
  remark?: string | null;
};

// ===================== plans =====================

export type AssignPlanRequest = {
  packageCode: string;
  ttlDays?: number | null;
  remark?: string | null;
};

export type RenewPlanRequest = {
  ttlDays: number;
  remark?: string | null;
};

export type RevokePlanRequest = {
  reason: string;
  notifyAdmin?: boolean;
};

// ===================== main admin =====================

export type TransferMainAdminMode = 'existing' | 'new';
export type OldAdminHandling = 'demote_admin' | 'demote_member' | 'disable';

export type TransferMainAdminRequest = {
  mode: TransferMainAdminMode;
  oldAdminHandling: OldAdminHandling;
  userId?: string | null;
  username?: string | null;
  realName?: string | null;
  mobile?: string | null;
  email?: string | null;
};

export type TransferMainAdminView = {
  newAdminUserId: string;
  newAdminUsername: string;
  resetToken: string;
  resetExpiresAt: string;
};

// ===================== impersonate =====================

export type ImpersonateRequest = {
  asUserId: string;
};

export type ImpersonateNonceView = {
  redeemNonce: string;
  targetTenantCode: string;
  expiresInSec: number;
};

// ===================== status / remark / name =====================

/** 暂停租户请求体（动词端点 POST /{tenantId}/suspend）。reason 写入操作日志。 */
export type TenantSuspendRequest = {
  reason?: string | null;
};

// ===================== usage / members / packages / ops users =====================

export type LoginTrendPoint = {
  /** YYYY-MM-DD（北京时区）。 */
  date: string;
  count: number;
};

/** 租户使用情况指标（OPS-T-009）。 */
export type OpsTenantUsageView = {
  memberCount: number;
  activeMembers30d: number;
  loginTrend: LoginTrendPoint[];
};

/** 运营端成员分页项（OPS-T-012）。 */
export type OpsTenantMemberItem = {
  userId: string;
  username: string;
  realName: string | null;
  mobile: string | null;
  email: string | null;
  /** 0=停用 / 1=正常。 */
  status: number;
  /** 是否当前主管理员。 */
  mainAdmin: boolean;
  lastLoginAt: string | null;
};

export type OpsTenantMemberPageView = {
  items: OpsTenantMemberItem[];
  total: number;
  page: number;
  pageSize: number;
};

/** 套餐目录项（OPS-T-011）。 */
export type OpsPackageCatalogItemView = {
  code: string;
  name: string;
  description: string | null;
  /** 套餐状态（boot 统一大写枚举）。仅 ACTIVE 可被勾选。 */
  status: 'DRAFT' | 'ACTIVE' | 'INACTIVE' | 'ARCHIVED';
  /** null=永久。 */
  defaultTtlDays: number | null;
  isSystem: boolean | null;
};

/** 运营账号候选人（选客户经理用，OPS-T-002）。 */
export type OpsUserView = {
  userId: string;
  username: string;
  realName: string | null;
};

export type UpdateTenantRemarkRequest = {
  remark?: string | null;
};

export type UpdateTenantNameRequest = {
  newName: string;
  reason: string;
};

// ===================== events / audit logs =====================

export type TenantEventItem = {
  id: string;
  action: string;
  objectType: string | null;
  objectTypeLabel: string | null;
  objectId: string | null;
  objectDesc: string | null;
  operatorId: string | null;
  operatorName: string | null;
  createdAt: string | null;
};

export type TenantEventPageView = {
  items: TenantEventItem[];
  total: number;
  page: number;
  pageSize: number;
};

export type TenantAuditLogItem = TenantEventItem & {
  requestId: string | null;
  statusCode: number | null;
};

export type TenantAuditLogPageView = {
  items: TenantAuditLogItem[];
  total: number;
  page: number;
  pageSize: number;
};

// ===================== client =====================

export function opsTenantApi(http: HttpClient) {
  const base = '/api/v1/ops/tenants';
  return {
    async createTenant(body: CreateTenantRequest): Promise<CreatedTenantView> {
      const res = await http.post<CreatedTenantView>(base, body);
      return res.data;
    },
    async listTenants(query?: ListTenantsQuery): Promise<TenantListResponse> {
      const res = await http.get<TenantListResponse>(base, { params: toWirePageParams(query) });
      return res.data;
    },
    /** 导出租户列表为 CSV（OPS-T-013，UTF-8 BOM，Excel 友好）。沿用列表筛选/排序参数。 */
    async exportTenants(query?: ListTenantsQuery): Promise<Blob> {
      const res = await http.get<Blob>(`${base}/export`, {
        params: query,
        responseType: 'blob',
      });
      return res.data;
    },
    async getTenantDetail(id: string): Promise<OpsTenantDetailView> {
      const res = await http.get<OpsTenantDetailView>(`${base}/${encodeURIComponent(id)}`);
      return res.data;
    },
    async reissueResetLink(id: string): Promise<ReissuedResetLinkView> {
      const res = await http.post<ReissuedResetLinkView>(
        `${base}/${encodeURIComponent(id)}/password-reset-link`,
      );
      return res.data;
    },
    async unlockLoginAttempt(tenantId: string, userId: string): Promise<void> {
      await http.delete(
        `${base}/${encodeURIComponent(tenantId)}/members/${encodeURIComponent(userId)}/login-lock`,
      );
    },
    async addSeatAddon(tenantId: string, body: AddSeatAddonRequest): Promise<void> {
      await http.post(`${base}/${encodeURIComponent(tenantId)}/seat-addons`, body);
    },
    async assignPlan(tenantId: string, body: AssignPlanRequest): Promise<void> {
      await http.post(`${base}/${encodeURIComponent(tenantId)}/plans`, body);
    },
    async renewPlan(tenantId: string, packageCode: string, body: RenewPlanRequest): Promise<void> {
      await http.post(
        `${base}/${encodeURIComponent(tenantId)}/plans/${encodeURIComponent(packageCode)}/renewals`,
        body,
      );
    },
    async revokePlan(
      tenantId: string,
      packageCode: string,
      body: RevokePlanRequest,
    ): Promise<void> {
      await http.request({
        method: 'DELETE',
        url: `${base}/${encodeURIComponent(tenantId)}/plans/${encodeURIComponent(packageCode)}`,
        data: body,
      });
    },
    async transferMainAdmin(
      tenantId: string,
      body: TransferMainAdminRequest,
    ): Promise<TransferMainAdminView> {
      const res = await http.post<TransferMainAdminView>(
        `${base}/${encodeURIComponent(tenantId)}/main-admin`,
        body,
      );
      return res.data;
    },
    async impersonate(tenantId: string, body: ImpersonateRequest): Promise<ImpersonateNonceView> {
      const res = await http.post<ImpersonateNonceView>(
        `${base}/${encodeURIComponent(tenantId)}/impersonate`,
        body,
      );
      return res.data;
    },
    /** 暂停租户（动词端点）。 */
    async suspend(tenantId: string, body?: TenantSuspendRequest): Promise<void> {
      await http.post(`${base}/${encodeURIComponent(tenantId)}/suspend`, body ?? {});
    },
    /** 恢复租户（动词端点）。 */
    async resume(tenantId: string): Promise<void> {
      await http.post(`${base}/${encodeURIComponent(tenantId)}/resume`, {});
    },
    async updateRemark(tenantId: string, body: UpdateTenantRemarkRequest): Promise<void> {
      await http.patch(`${base}/${encodeURIComponent(tenantId)}/remark`, body);
    },
    async updateName(tenantId: string, body: UpdateTenantNameRequest): Promise<void> {
      await http.patch(`${base}/${encodeURIComponent(tenantId)}/name`, body);
    },
    async listTenantEvents(
      tenantId: string,
      query?: { page?: number; pageSize?: number },
    ): Promise<TenantEventPageView> {
      const res = await http.get<TenantEventPageView>(
        `${base}/${encodeURIComponent(tenantId)}/events`,
        { params: toWirePageParams(query) },
      );
      return res.data;
    },
    async listTenantAuditLogs(
      tenantId: string,
      query?: { page?: number; pageSize?: number },
    ): Promise<TenantAuditLogPageView> {
      const res = await http.get<TenantAuditLogPageView>(
        `${base}/${encodeURIComponent(tenantId)}/audit-logs`,
        { params: toWirePageParams(query) },
      );
      return res.data;
    },
    /** 租户使用情况：成员数/30 天活跃/登录趋势（OPS-T-009）。 */
    async getTenantUsage(tenantId: string): Promise<OpsTenantUsageView> {
      const res = await http.get<OpsTenantUsageView>(
        `${base}/${encodeURIComponent(tenantId)}/usage`,
      );
      return res.data;
    },
    /** 运营端成员分页查询（OPS-T-012）。 */
    async listTenantMembers(
      tenantId: string,
      query?: { keyword?: string; page?: number; pageSize?: number },
    ): Promise<OpsTenantMemberPageView> {
      const res = await http.get<OpsTenantMemberPageView>(
        `${base}/${encodeURIComponent(tenantId)}/members`,
        { params: toWirePageParams(query) },
      );
      return res.data;
    },
  };
}

/** 套餐目录（OPS-T-011）。独立路径 `/api/v1/ops/packages`。 */
export function opsPackageApi(http: HttpClient) {
  return {
    async listPackages(): Promise<OpsPackageCatalogItemView[]> {
      const res = await http.get<OpsPackageCatalogItemView[]>('/api/v1/ops/packages');
      return res.data;
    },
  };
}

/** 运营账号候选人查询（选客户经理用，OPS-T-002）。 */
export function opsUserApi(http: HttpClient) {
  return {
    async listOpsUsers(keyword?: string): Promise<OpsUserView[]> {
      const res = await http.get<OpsUserView[]>('/api/v1/ops/users', {
        params: keyword ? { keyword } : undefined,
      });
      return res.data;
    },
  };
}
