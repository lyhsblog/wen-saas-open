import type { HttpClient } from './http';
import type {
  EncryptionOverview,
  ExportRequestPage,
  ExportRequestStatus,
  ExportRequestView,
} from './compliance';

/**
 * 运营端「数据隐私与合规审计」。对应 boot `/api/v1/ops/compliance/**`。
 *
 * 运营跨租户只读监控导出审批：列表按运营 dataScope 收敛（OWNED 仅看 csm_user_id=self
 * 的租户）+ 敏感字段加密底座自查（平台事实，全平台一致）。权限点 ops.compliance.view。
 * 雪花 Long 全程字符串。
 */

/** 运营端审计查询（page 1-based，size≤200）。 */
export type OpsComplianceQuery = {
  tenantId?: string | undefined;
  status?: ExportRequestStatus | undefined;
  keyword?: string | undefined;
  page?: number | undefined;
  size?: number | undefined;
};

type RawPage = Omit<ExportRequestPage, 'total'> & { total: string | number };

export function opsComplianceApi(http: HttpClient) {
  const base = '/api/v1/ops/compliance';
  return {
    /** 跨租户超量导出审批审计列表。 */
    async list(query?: OpsComplianceQuery): Promise<ExportRequestPage> {
      const q = query ?? {};
      const params = {
        ...(q.tenantId ? { tenantId: q.tenantId } : {}),
        ...(q.status ? { status: q.status } : {}),
        ...(q.keyword ? { keyword: q.keyword } : {}),
        ...(q.page ? { page: q.page } : {}),
        ...(q.size ? { size: q.size } : {}),
      };
      const res = await http.get<RawPage>(`${base}/export-requests`, { params });
      return { ...res.data, total: Number(res.data.total) };
    },
    /** 跨租户导出申请详情。 */
    async getDetail(id: string): Promise<ExportRequestView> {
      const res = await http.get<ExportRequestView>(
        `${base}/export-requests/${encodeURIComponent(id)}`,
      );
      return res.data;
    },
    /** 敏感字段加密底座自查（算法 / 密钥版本 / 字段登记表 / 掩码规则）。 */
    async encryption(): Promise<EncryptionOverview> {
      const res = await http.get<EncryptionOverview>(`${base}/encryption`);
      return res.data;
    },
  };
}
