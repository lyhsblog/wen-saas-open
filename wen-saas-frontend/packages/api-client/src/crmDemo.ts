import type { HttpClient } from './http';

export interface CrmCustomerListItem {
  id: string;
  name: string;
  code: string | null;
  status: string;
  statusLabel: string;
  /** 客户来源：字典分组 crm_demo.customer_source 的项 code；中文名按字典项缓存翻译。 */
  source: string | null;
  ownerId: string | null;
  /** 负责人姓名（后端 @TransField 回填；负责人已删除时为 null，按 ownerId 兜底）。 */
  ownerName: string | null;
  ownerDeptId: string | null;
  createdAt: string;
  /** 自定义字段视图（enum/ref 带 __label 后缀键）。 */
  extra: Record<string, unknown>;
}

export interface CrmCustomerDetail extends CrmCustomerListItem {
  /** 归属部门名（后端 @TransField 回填）。 */
  ownerDeptName: string | null;
  creatorId: string | null;
  updatedAt: string;
}

/** 写操作统一返回的简报。 */
export interface CrmCustomerBrief {
  id: string;
  name: string;
  status: string;
  statusLabel: string;
}

export interface CrmCustomerPage {
  records: CrmCustomerListItem[];
  /** 雪花/长整型在 JSON 中序列化为字符串。 */
  total: string;
}

/** 管理列表（GET /，需 crm_demo.customer.view_manage）：可按任意负责人筛选。 */
export interface CrmCustomerListQuery {
  status?: string;
  keyword?: string;
  ownerId?: string;
  source?: string;
  page?: number;
  size?: number;
}

/** 我的客户（GET /mine，需 crm_demo.customer.view）：负责人恒为当前用户，无 ownerId。 */
export type CrmCustomerMineQuery = Omit<CrmCustomerListQuery, 'ownerId'>;

export interface CrmCustomerCreateRequest {
  name: string;
  code?: string;
  status?: string;
  source?: string;
  ownerId?: string;
  ownerDeptId?: string;
  custom?: Record<string, unknown>;
}

export interface CrmCustomerUpdateRequest {
  name: string;
  code?: string;
  source?: string;
  ownerId?: string;
  ownerDeptId?: string;
  custom?: Record<string, unknown>;
}

/** CRM 客户 Demo API。objectCode = crm_demo.customer。 */
export function crmCustomerApi(http: HttpClient) {
  const base = '/api/v1/biz/crm_demo/customers';
  return {
    async list(
      query: CrmCustomerListQuery,
      cfEquals?: Record<string, string>,
    ): Promise<CrmCustomerPage> {
      const res = await http.get<CrmCustomerPage>(base, { params: { ...query, ...cfEquals } });
      return res.data;
    },
    async listMine(
      query: CrmCustomerMineQuery,
      cfEquals?: Record<string, string>,
    ): Promise<CrmCustomerPage> {
      const res = await http.get<CrmCustomerPage>(`${base}/mine`, {
        params: { ...query, ...cfEquals },
      });
      return res.data;
    },
    async get(id: string): Promise<CrmCustomerDetail> {
      const res = await http.get<CrmCustomerDetail>(`${base}/${encodeURIComponent(id)}`);
      return res.data;
    },
    async create(body: CrmCustomerCreateRequest): Promise<CrmCustomerBrief> {
      const res = await http.post<CrmCustomerBrief>(base, body);
      return res.data;
    },
    async update(id: string, body: CrmCustomerUpdateRequest): Promise<void> {
      await http.put(`${base}/${encodeURIComponent(id)}`, body);
    },
    async remove(id: string): Promise<void> {
      await http.delete(`${base}/${encodeURIComponent(id)}`);
    },
    async transition(id: string, target: string): Promise<void> {
      await http.post(`${base}/${encodeURIComponent(id)}/transition`, null, { params: { target } });
    },
  };
}
