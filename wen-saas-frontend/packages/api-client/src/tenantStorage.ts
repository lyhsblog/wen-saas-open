import type { HttpClient } from './http';

/**
 * 租户后台「系统管理 · 存储设置」。对应 boot `/api/v1/base/storage`。
 *
 * 支持「平台默认存储」与「自有对象存储（阿里云 OSS / 腾讯云 COS）」。AccessKey Secret 永不回显；
 * 测试连接 / 切换时 accessKeySecret 传掩码 "********" = 沿用已保存凭证，重填才更新。
 * 切换前必须测试连接通过。
 */

export type StorageConfigView = {
  id: string;
  /** "aliyun_oss" / "tencent_cos" / "local"。 */
  backendType: string;
  /** OSS 为 endpoint 域名；COS 为 region code（如 ap-guangzhou）。 */
  endpoint: string | null;
  bucket: string | null;
  /** OSS 为 AccessKeyId；COS 为 SecretId。 */
  accessKeyId: string | null;
  pathPrefix: string | null;
  /** "active" / "archived"。 */
  status: string;
};

export type StorageStatusView = {
  /** true=已切自有对象存储；false=平台默认存储。 */
  usingCustom: boolean;
  /** usingCustom=false 时为 null。 */
  config: StorageConfigView | null;
};

export type CustomOssRequest = {
  /** "aliyun_oss" / "tencent_cos"。 */
  backendType: 'aliyun_oss' | 'tencent_cos';
  /** OSS 为 endpoint 域名；COS 为 region code（如 ap-guangzhou）。 */
  endpoint: string;
  bucket: string;
  /** OSS 为 AccessKeyId；COS 为 SecretId。 */
  accessKeyId: string;
  /** OSS 为 AccessKeySecret；COS 为 SecretKey。传掩码 "********" = 沿用当前已保存凭证。 */
  accessKeySecret: string;
  pathPrefix?: string | null;
};

export function tenantStorageApi(http: HttpClient) {
  const base = '/api/v1/base/storage';
  return {
    async current(): Promise<StorageStatusView> {
      const res = await http.get<StorageStatusView>(base);
      return res.data;
    },
    /** 测试连接（不落库；失败后端抛错，前端展示 message）。 */
    async testConnection(body: CustomOssRequest): Promise<void> {
      await http.post(`${base}/test-connection`, body);
    },
    /** 切换到自有 OSS（先测试连接，通过后落库）。 */
    async switchToCustom(body: CustomOssRequest): Promise<StorageConfigView> {
      const res = await http.put<StorageConfigView>(`${base}/custom`, body);
      return res.data;
    },
    /** 切换回平台默认存储。 */
    async switchToPlatform(): Promise<void> {
      await http.put(`${base}/platform`);
    },
  };
}
