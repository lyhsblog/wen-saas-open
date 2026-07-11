import type { HttpClient } from './http';

/**
 * 租户后台「系统管理 · 套餐与账号」。对应 boot `/api/v1/base/account`。
 *
 * 纯只读账户面板：套餐 / 席位 / 到期 / 变更历史。无写操作；续费 / 加购走运营线下。
 */

export type PlanModule = {
  moduleCode: string;
  moduleName: string;
};

export type PlanItem = {
  packageCode: string;
  name: string;
  /** ISO；null=永久有效。 */
  expiresAt: string | null;
  /** true=已过期（数据只读保留）。 */
  readonly: boolean;
  /** true=基础包（系统标配）。 */
  systemDefault: boolean;
  modules: PlanModule[];
};

export type SupportContact = {
  phone: string | null;
  email: string | null;
};

export type AccountOverview = {
  tenantId: string;
  tenantNo: string | null;
  packageCount: number;
  /** 30 天内到期的套餐数。 */
  expiringSoonCount: number;
  /** 基础席位（=成员上限；null=不限制）。 */
  baseSeats: number | null;
  addonSeats: number;
  usedSeats: number;
  /** 所有套餐中最早的到期日（ISO，null=全部永久）。 */
  nearestExpireAt: string | null;
  plans: PlanItem[];
  support: SupportContact;
};

export type ChangeHistoryItem = {
  id: string;
  action: string;
  objectType: string | null;
  objectDesc: string | null;
  operatorName: string | null;
  createdAt: string;
};

export type ChangeHistoryPage = {
  items: ChangeHistoryItem[];
  total: number;
  page: number;
  pageSize: number;
};

export function tenantAccountApi(http: HttpClient) {
  const base = '/api/v1/base/account';
  return {
    async overview(): Promise<AccountOverview> {
      const res = await http.get<AccountOverview>(`${base}/overview`);
      return res.data;
    },
    async changeHistory(page = 1, size = 10): Promise<ChangeHistoryPage> {
      const res = await http.get<ChangeHistoryPage>(`${base}/change-history`, {
        params: { page, size },
      });
      return res.data;
    },
  };
}
