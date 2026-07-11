import type { HttpClient } from './http';

/**
 * 运营端「系统管理 · 系统参数」。对应 boot `/api/v1/ops/settings/**`（3 endpoint）。
 *
 * 参数运行时可改（PUT 后经 RocketMQ 广播失效本地缓存，无需重启）。
 * 每个分组返回若干 key/value 项，value 为动态 JSON 值（string/number/boolean/...），
 * 前端按 typeof value 选择渲染控件，不硬编码字段清单。
 */

/** 参数分组。 */
export type SettingCategory = 'platform_info' | 'tenant_default' | 'security_policy';

/** 单条参数。value 类型由后端 jsonb 决定，前端按 typeof 渲染。 */
export type SettingItem = {
  key: string;
  /**
   * 动态 JSON 值（string/number/boolean/...）。
   * 密钥类 key（名含 secret / access_key / password）的非空值后端一律回掩码 "********"，不出明文。
   */
  value: unknown;
  description: string;
  updatedAt: string | null;
  /** 更新者 userId（雪花 Long → 字符串，可能为 null）。 */
  updatedBy: string | null;
};

export type SettingCategoryView = {
  category: SettingCategory;
  items: SettingItem[];
};

/**
 * 批量更新：key -> value 映射，value 类型与读取时一致。
 * 密钥类 key 提交空白或掩码 "********" = 不变更（后端跳过该 key 不覆写）。
 */
export type OpsSettingsUpdateRequest = {
  values: Record<string, unknown>;
};

export function opsSettingApi(http: HttpClient) {
  const base = '/api/v1/ops/settings';
  return {
    async listAll(): Promise<SettingCategoryView[]> {
      const res = await http.get<SettingCategoryView[]>(base);
      return res.data;
    },
    async getCategory(category: SettingCategory): Promise<SettingCategoryView> {
      const res = await http.get<SettingCategoryView>(`${base}/${category}`);
      return res.data;
    },
    /** 批量更新某分组参数（全量提交本分组改动的 key；密钥类 key 空白/掩码不覆写）。 */
    async updateCategory(category: SettingCategory, body: OpsSettingsUpdateRequest): Promise<void> {
      await http.put(`${base}/${category}`, body);
    },
  };
}
