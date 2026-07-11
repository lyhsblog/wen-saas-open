import type { HttpClient } from './http';

/**
 * 租户后台「系统管理 · 安全合规 · 防截屏水印开关」。对应 boot `/api/v1/base/security/watermark`。
 *
 * 租户级开关、默认开启；当前开关态同时经 `/api/v1/me/capabilities` 的 `screenshotWatermark`
 * 下发用于全页水印渲染门控。读取任意成员可，写入后端按主管理员门控（无权 403）。
 */

export type WatermarkConfig = {
  enabled: boolean;
};

export function tenantWatermarkApi(http: HttpClient) {
  const base = '/api/v1/base/security/watermark';
  return {
    async get(): Promise<WatermarkConfig> {
      const res = await http.get<WatermarkConfig>(base);
      return res.data;
    },
    async update(enabled: boolean): Promise<WatermarkConfig> {
      const res = await http.put<WatermarkConfig>(base, { enabled });
      return res.data;
    },
  };
}
