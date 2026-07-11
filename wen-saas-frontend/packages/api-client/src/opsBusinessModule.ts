import type { HttpClient } from './http';

/**
 * 运营端「业务模块管理」（只读内省）。对应 boot `/api/v1/ops/business-modules/**`。
 *
 * 底座 SPI 注册表内省聚合：清单（按 code 升序）+ 详情（贡献能力计数 / 依赖 / API 前缀 / 关联套餐）。
 * 只读，不含上线/下线/版本回滚等写操作（产品无模块上下线概念）。
 */

/** 列表项（精简视图）。registeredAt 为 ISO-8601 字符串。 */
export type BusinessModuleListItem = {
  code: string;
  name: string;
  version: string;
  icon: string | null;
  registeredAt: string | null;
  dependencyCount: number;
  /** 5 类扩展点贡献合计（权限+字典+卡片+H5+通知；菜单不在SPI声明，sys_menu为唯一权威）。 */
  contributionCount: number;
};

/** 贡献的扩展点计数。菜单不在此列（sys_menu DB权威，非SPI声明）。 */
export type ModuleContribution = {
  permissions: number;
  dictGroups: number;
  dashboardCards: number;
  h5Pages: number;
  notificationTemplates: number;
};

/** 关联套餐项（包含该模块的套餐）。status=DRAFT/ACTIVE/INACTIVE/ARCHIVED。 */
export type RelatedPackageItem = {
  code: string;
  name: string;
  status: string;
};

/** 详情。 */
export type BusinessModuleDetail = {
  code: string;
  name: string;
  description: string | null;
  version: string;
  icon: string | null;
  registeredAt: string | null;
  dependencies: string[];
  apiPrefixes: string[];
  contribution: ModuleContribution;
  relatedPackages: RelatedPackageItem[];
};

export function opsBusinessModuleApi(http: HttpClient) {
  const base = '/api/v1/ops/business-modules';
  return {
    /** 业务模块清单（按 code 升序）。 */
    async listModules(): Promise<BusinessModuleListItem[]> {
      const res = await http.get<BusinessModuleListItem[]>(base);
      return res.data;
    },
    /** 业务模块详情。模块不存在 → 404 MODULE_NOT_FOUND。 */
    async getModule(code: string): Promise<BusinessModuleDetail> {
      const res = await http.get<BusinessModuleDetail>(`${base}/${encodeURIComponent(code)}`);
      return res.data;
    },
  };
}
