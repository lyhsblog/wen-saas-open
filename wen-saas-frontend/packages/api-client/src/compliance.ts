import type { HttpClient } from './http';

/**
 * 数据隐私与合规 · 超量导出审批。租户侧
 * `/api/v1/base/compliance/**`、运营侧 `/api/v1/ops/compliance/**`（见 opsCompliance.ts）。
 * 本文件放两端共享视图类型 + 租户侧工厂 `tenantComplianceApi`。
 *
 * 雪花 Long（id / tenantId / applicantId / approverId）JSON 序列化为字符串，前端类型用
 * string、URL/queryKey 不转 number 防精度丢失；总览计数（long）亦为字符串，工厂内 Number()
 * 归一供展示。estimatedCount / approvalRequiredCount / timeoutHours 为 int，回包即 number。
 *
 * 审批触发由「各导出对象是否需审批」开关驱动（不再按数量阈值评估）：策略含各对象开关，
 * 发起导出按 objectCode 命中开关，需审批转 PENDING、无需审批后端回 409（EXPORT_NOT_REQUIRED）。
 */

/** 审批状态。 */
export type ExportRequestStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

/** 审批权限：仅主管理员 / 持权限点的角色。 */
export type ExportApprovalAuthority = 'MAIN_ADMIN' | 'ROLE_PERMISSION';

/** 超量导出审批申请视图（列表项 / 详情共用）。 */
export type ExportRequestView = {
  id: string;
  requestNo: string;
  tenantId: string | null;
  /** 仅运营跨租户视图填充；租户侧为 null。 */
  tenantName: string | null;
  objectType: string;
  objectCode: string | null;
  filterSummary: string | null;
  /** 预估条数（仅作申请信息展示，不再驱动审批）。 */
  estimatedCount: number;
  reason: string;
  status: ExportRequestStatus;
  applicantId: string | null;
  applicantName: string | null;
  applicantDeptName: string | null;
  approverId: string | null;
  approverName: string | null;
  approvalComment: string | null;
  appliedAt: string | null;
  decidedAt: string | null;
  /** 审批通过后投递的任务中心任务 ID（V1.52.0；批准前为 null）。 */
  taskId: string | null;
};

/** 分页结果。total 后端为 long（字符串），工厂转回 number 供分页计算。 */
export type ExportRequestPage = {
  items: ExportRequestView[];
  total: number;
  page: number;
  size: number;
};

/** 导出对象的审批开关（策略页一行；业务声明 + 租户覆盖合并的生效值）。 */
export type ExportObjectRule = {
  code: string;
  name: string;
  module: string;
  requiresApproval: boolean;
};

/** 超量导出策略视图：各对象审批开关 + 审批权限 + 超时提醒。 */
export type ExportPolicyView = {
  objects: ExportObjectRule[];
  approvalAuthority: ExportApprovalAuthority;
  timeoutHours: number | null;
};

/** 合规总览指标（超量导出审批维度）。计数后端为 long（字符串），工厂 Number() 归一。 */
export type ComplianceOverview = {
  pendingCount: number;
  overtimePendingCount: number;
  approved30d: number;
  rejected30d: number;
  /** 当前需审批的导出对象数。 */
  approvalRequiredCount: number;
  approvalAuthority: ExportApprovalAuthority;
  timeoutHours: number | null;
};

/** 发起导出申请请求（objectCode 必填，定位审批开关；estimatedCount 仅作申请信息）。 */
export type SubmitExportRequest = {
  objectCode: string;
  objectType?: string | undefined;
  filterSummary?: string | undefined;
  estimatedCount?: number | undefined;
  reason: string;
};

/** 单个导出对象的审批开关设置。 */
export type ExportObjectToggle = {
  code: string;
  requiresApproval: boolean;
};

/** 更新策略请求：各对象审批开关 + 审批权限 + 超时提醒。 */
export type UpdateExportPolicyRequest = {
  objects: ExportObjectToggle[];
  approvalAuthority: ExportApprovalAuthority;
  timeoutHours?: number | null | undefined;
};

/** 导出申请列表查询（page 1-based，size≤200）。 */
export type ExportRequestQuery = {
  status?: ExportRequestStatus | undefined;
  page?: number | undefined;
  size?: number | undefined;
};

/* ============ 客户全量数据导出（GDPR-style） ============ */

/** 全量导出状态：打包中 / 已完成 / 失败。 */
export type DataExportStatus = 'PACKAGING' | 'COMPLETED' | 'FAILED';

/** 敏感字段处理：完整 / 脱敏。 */
export type DataExportMode = 'FULL' | 'MASKED';

/**
 * 全量数据导出申请视图。{@code fileSize} 后端为 long（字符串），工厂 Number() 归一；
 * {@code downloadToken} 为下载令牌占位，{@code expiresAt} 为下载链接失效时间（完成后 7 天）。
 */
export type DataExportView = {
  id: string;
  requestNo: string;
  tenantId: string | null;
  reason: string;
  dataMode: DataExportMode;
  scopeSummary: string | null;
  status: DataExportStatus;
  itemSummary: string | null;
  fileName: string | null;
  fileSize: number | null;
  downloadToken: string | null;
  expiresAt: string | null;
  applicantId: string | null;
  applicantName: string | null;
  requestedAt: string | null;
  completedAt: string | null;
  errorMessage: string | null;
};

/** 全量导出申请分页结果。 */
export type DataExportPage = {
  items: DataExportView[];
  total: number;
  page: number;
  size: number;
};

/** 发起全量导出请求。 */
export type SubmitDataExportRequest = {
  reason: string;
  dataMode: DataExportMode;
};

/* ============ 数据留存与软删除策略 ============ */

/** 软删除 / 留存策略矩阵的一行（声明式）。 */
export type RetentionPolicyItem = {
  objectType: string;
  deleteBehavior: string;
  /** 是否涉及物理硬删（true=会彻底清理）。 */
  hardDelete: boolean;
  note: string | null;
};

/** 文件回收站实时统计。计数后端为 long（字符串），工厂 Number() 归一。 */
export type FileRecycleBinStats = {
  fileCount: number;
  totalBytes: number;
  expiringSoonCount: number;
  overdueCount: number;
  expiringWindowDays: number;
};

/** 数据留存与软删除策略总览。 */
export type RetentionOverview = {
  policies: RetentionPolicyItem[];
  recycleBin: FileRecycleBinStats;
  fileRetentionDays: number;
  purgeSchedule: string;
};

/* ============ 敏感字段加密 ============ */

/** 敏感字段登记表的一行（声明式）。 */
export type SensitiveFieldRow = {
  field: string;
  sourceModule: string;
  /** 是否落库加密（AES-256-GCM）。 */
  encryptedAtRest: boolean;
  /** 加密底座说明；未加密为 null。 */
  cipher: string | null;
  /** 列表掩码类型（PHONE / EMAIL / SECRET…）；无掩码为 null。 */
  maskType: string | null;
  /** 列表掩码示例；无掩码为 null。 */
  maskExample: string | null;
};

/** 列表掩码规则示例。 */
export type MaskRuleExample = {
  fieldType: string;
  sample: string;
  masked: string;
};

/** 敏感字段加密总览。字段均为字符串 / 布尔，无 long，无需归一。 */
export type EncryptionOverview = {
  algorithm: string;
  keyConfigured: boolean;
  activeKeyId: string;
  fields: SensitiveFieldRow[];
  maskRules: MaskRuleExample[];
};

type RawPage = Omit<ExportRequestPage, 'total'> & { total: string | number };
type RawOverview = Omit<
  ComplianceOverview,
  'pendingCount' | 'overtimePendingCount' | 'approved30d' | 'rejected30d'
> & {
  pendingCount: string | number;
  overtimePendingCount: string | number;
  approved30d: string | number;
  rejected30d: string | number;
};

function normalizePage(raw: RawPage): ExportRequestPage {
  return { ...raw, total: Number(raw.total) };
}

function normalizeOverview(raw: RawOverview): ComplianceOverview {
  return {
    ...raw,
    pendingCount: Number(raw.pendingCount),
    overtimePendingCount: Number(raw.overtimePendingCount),
    approved30d: Number(raw.approved30d),
    rejected30d: Number(raw.rejected30d),
  };
}

type RawDataExportView = Omit<DataExportView, 'fileSize'> & { fileSize: string | number | null };
type RawDataExportPage = Omit<DataExportPage, 'items' | 'total'> & {
  items: RawDataExportView[];
  total: string | number;
};

function normalizeDataExport(raw: RawDataExportView): DataExportView {
  return { ...raw, fileSize: raw.fileSize == null ? null : Number(raw.fileSize) };
}

function normalizeDataExportPage(raw: RawDataExportPage): DataExportPage {
  return { ...raw, items: raw.items.map(normalizeDataExport), total: Number(raw.total) };
}

function listParams(query?: ExportRequestQuery) {
  const q = query ?? {};
  return {
    ...(q.status ? { status: q.status } : {}),
    ...(q.page ? { page: q.page } : {}),
    ...(q.size ? { size: q.size } : {}),
  };
}

export function tenantComplianceApi(http: HttpClient) {
  const base = '/api/v1/base/compliance';
  return {
    /** 合规总览指标。 */
    async overview(): Promise<ComplianceOverview> {
      const res = await http.get<RawOverview>(`${base}/overview`);
      return normalizeOverview(res.data);
    },
    /** 发起导出申请（按对象审批开关：需审批转 PENDING，无需审批后端回 409）。 */
    async submit(body: SubmitExportRequest): Promise<ExportRequestView> {
      const res = await http.post<ExportRequestView>(`${base}/export-requests`, body);
      return res.data;
    },
    /** 我发起的导出申请。 */
    async myRequests(query?: ExportRequestQuery): Promise<ExportRequestPage> {
      const res = await http.get<RawPage>(`${base}/export-requests/mine`, {
        params: listParams(query),
      });
      return normalizePage(res.data);
    },
    /** 本租户全部导出申请（需审批权限）。 */
    async allRequests(query?: ExportRequestQuery): Promise<ExportRequestPage> {
      const res = await http.get<RawPage>(`${base}/export-requests`, { params: listParams(query) });
      return normalizePage(res.data);
    },
    /** 导出申请详情。 */
    async getDetail(id: string): Promise<ExportRequestView> {
      const res = await http.get<ExportRequestView>(
        `${base}/export-requests/${encodeURIComponent(id)}`,
      );
      return res.data;
    },
    /** 批准导出申请。 */
    async approve(id: string, comment?: string): Promise<void> {
      await http.post(`${base}/export-requests/${encodeURIComponent(id)}/approve`, {
        comment: comment ?? null,
      });
    },
    /** 拒绝导出申请。 */
    async reject(id: string, comment?: string): Promise<void> {
      await http.post(`${base}/export-requests/${encodeURIComponent(id)}/reject`, {
        comment: comment ?? null,
      });
    },
    /** 查看导出审批策略。 */
    async getPolicy(): Promise<ExportPolicyView> {
      const res = await http.get<ExportPolicyView>(`${base}/policy`);
      return res.data;
    },
    /** 更新导出审批策略。 */
    async updatePolicy(body: UpdateExportPolicyRequest): Promise<void> {
      await http.put(`${base}/policy`, body);
    },

    /** 发起全量数据导出（仅主管理员；月限 1 次）。 */
    async submitDataExport(body: SubmitDataExportRequest): Promise<DataExportView> {
      const res = await http.post<RawDataExportView>(`${base}/data-exports`, body);
      return normalizeDataExport(res.data);
    },
    /** 本租户全量数据导出申请列表。 */
    async listDataExports(page = 1, size = 20): Promise<DataExportPage> {
      const res = await http.get<RawDataExportPage>(`${base}/data-exports`, {
        params: { page, size },
      });
      return normalizeDataExportPage(res.data);
    },
    /** 全量数据导出申请详情。 */
    async getDataExportDetail(id: string): Promise<DataExportView> {
      const res = await http.get<RawDataExportView>(
        `${base}/data-exports/${encodeURIComponent(id)}`,
      );
      return normalizeDataExport(res.data);
    },
    /** 换取导出包下载 URL（校验完成 + 7 天有效期；后端 ProblemDetail 409/403 兜底）。 */
    async downloadDataExportUrl(id: string): Promise<string> {
      const res = await http.get<{ url: string }>(
        `${base}/data-exports/${encodeURIComponent(id)}/download`,
      );
      return res.data.url;
    },
  };
}
