import type { HttpClient } from './http';

/**
 * 租户后台「系统管理 · 公司信息」。对应 boot `/api/v1/base/company-profile`。
 *
 * 公司名称（=租户名）走运营侧警示流程，本模块只读不改名；可改：行业 / 规模 / 备注 / Logo / 路由位。
 * 雪花 id（tenantId / logoFileId）后端序列化为字符串，前端按 string 处理。
 */

export type CompanyProfile = {
  tenantId: string;
  /** 公司名称（=租户名，只读，改名走运营警示流程）。 */
  name: string;
  /** 白标路由位（code）。 */
  routeSlot: string;
  fullName: string | null;
  industry: string | null;
  scale: string | null;
  /** 备注。 */
  description: string | null;
  logoFileId: string | null;
  /** 实时签发的 Logo 下载 URL；为空时前端回退默认 Logo。 */
  logoUrl: string | null;
  /** 路由位最近一次修改时间（ISO，null=从未改过）。 */
  codeChangedAt: string | null;
  /** 路由位下次可改时间（ISO，null=立即可改）。 */
  routeSlotChangeableAt: string | null;
};

export type UpdateCompanyBasicRequest = {
  industry?: string | null;
  scale?: string | null;
  description?: string | null;
};

/** 字典选项：code 入库、name 展示。 */
export type DictOption = {
  code: string;
  name: string;
};

/** 行业 / 规模可选项（来自字典 INDUSTRY / COMPANY_SIZE，含租户自定义项）。 */
export type CompanyOptions = {
  industries: DictOption[];
  scales: DictOption[];
};

export function companyProfileApi(http: HttpClient) {
  const base = '/api/v1/base/company-profile';
  return {
    async get(): Promise<CompanyProfile> {
      const res = await http.get<CompanyProfile>(base);
      return res.data;
    },
    /** 行业 / 规模下拉项（company.view 鉴权，无需字典管理权限）。 */
    async getOptions(): Promise<CompanyOptions> {
      const res = await http.get<CompanyOptions>(`${base}/options`);
      return res.data;
    },
    async updateBasic(body: UpdateCompanyBasicRequest): Promise<void> {
      await http.put(base, body);
    },
    /** 修改路由位（30 天限频 + 全局唯一；冲突返回 409，格式非法 422）。 */
    async changeRouteSlot(routeSlot: string): Promise<void> {
      await http.put(`${base}/route-slot`, { routeSlot });
    },
    /** 上传公司 Logo（≤1MB，PNG/JPG/SVG）；返回设置后的最新公司信息。 */
    async uploadLogo(file: File): Promise<CompanyProfile> {
      const form = new FormData();
      form.append('file', file);
      const res = await http.post<CompanyProfile>(`${base}/logo`, form);
      return res.data;
    },
    /** 删除 Logo，恢复底座默认。 */
    async removeLogo(): Promise<void> {
      await http.delete(`${base}/logo`);
    },
  };
}
