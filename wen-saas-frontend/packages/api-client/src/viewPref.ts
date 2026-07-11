import type { HttpClient } from './http';

/** 后端列偏好读出契约。 */
export interface ColumnPrefDto {
  /** 是否存在用户配置；false 时两数组为空，前端按默认态处理。 */
  customized: boolean;
  customKeys: string[];
  hiddenKeys: string[];
}

/** 用户级列偏好 API。viewKey 为列表标识（如 crm_demo.customer）。 */
export function viewPrefApi(http: HttpClient) {
  const base = '/api/v1/base/view-pref';
  return {
    async get(viewKey: string): Promise<ColumnPrefDto> {
      const res = await http.get<ColumnPrefDto>(`${base}/${encodeURIComponent(viewKey)}`);
      return res.data;
    },
    async save(viewKey: string, customKeys: string[], hiddenKeys: string[]): Promise<void> {
      await http.put(`${base}/${encodeURIComponent(viewKey)}`, { customKeys, hiddenKeys });
    },
    async reset(viewKey: string): Promise<void> {
      await http.delete(`${base}/${encodeURIComponent(viewKey)}`);
    },
  };
}
