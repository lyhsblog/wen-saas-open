import type { HttpClient } from './http';

/**
 * 公开租户信息（免登录）。对应后端 `/api/v1/public/tenants/**`。
 * 供租户子域登录页渲染租户名 / Logo，并据 canonicalCode 处理旧子域名重定向。
 */
export type PublicTenantView = {
  id: string;
  /** 租户当前编码。 */
  code: string;
  /** 租户当前规范编码；与请求 code 不同 = 请求的是旧子域名别名，应整页跳规范子域。 */
  canonicalCode: string;
  name: string;
  /** 实时签发的 Logo 下载 URL（免登录可下），可空。 */
  logoUrl?: string | null;
  status: 'active' | 'paused' | 'expired';
};

export function publicTenantApi(http: HttpClient) {
  const base = '/api/v1/public/tenants';
  return {
    /** 按 code 查询租户公开信息；不存在返回 404。 */
    async byCode(code: string): Promise<PublicTenantView> {
      const res = await http.get<PublicTenantView>(`${base}/by-code/${encodeURIComponent(code)}`);
      return res.data;
    },
  };
}
