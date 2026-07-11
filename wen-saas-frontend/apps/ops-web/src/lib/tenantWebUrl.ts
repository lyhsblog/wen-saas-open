/**
 * 租户端（tenant-web）链接构造。租户标识在子域名：{tenantCode}.{基础域}，路径不含租户前缀。
 * VITE_TENANT_WEB_ORIGIN 为租户端「裸域 origin」（本地 http://localhost:5174、线上如
 * https://saas.xuwen.site），生成链接时把租户 code 前缀到 hostname（端口/协议保留）。
 */
const TENANT_WEB_BASE_ORIGIN: string =
  (import.meta.env.VITE_TENANT_WEB_ORIGIN as string | undefined) ?? 'http://localhost:5174';

export function tenantWebUrl(tenantCode: string, path: string): string {
  const url = new URL(path, TENANT_WEB_BASE_ORIGIN);
  url.hostname = `${tenantCode}.${url.hostname}`;
  return url.toString();
}

/** 租户子域名 host（{code}.基础域[:端口]），供界面展示。 */
export function tenantWebHost(tenantCode: string): string {
  return new URL(tenantWebUrl(tenantCode, '/')).host;
}

/**
 * H5（移动端）端「裸域 origin」。H5 端子域同为 {tenantCode}.{基础域}，但基础域比 PC 端多一段 `m.`
 * 前缀（线上 m.saas.xuwen.site、本地 m.localhost:5175），端口/域名与 tenant-web 不同故单独配置。
 */
const H5_WEB_BASE_ORIGIN: string =
  (import.meta.env.VITE_H5_WEB_ORIGIN as string | undefined) ?? 'http://m.localhost:5175';

/** H5（移动端）子域名 host（{code}.m.基础域[:端口]），供界面展示。 */
export function h5WebHost(tenantCode: string): string {
  const url = new URL('/', H5_WEB_BASE_ORIGIN);
  url.hostname = `${tenantCode}.${url.hostname}`;
  return url.host;
}
