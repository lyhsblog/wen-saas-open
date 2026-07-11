import type { HttpClient } from './http';
import type { FileUploadResult } from './fileCenter';

/**
 * 业务附件（business-only 文件）API：通用 `/api/v1/files` 端点的薄封装。
 *
 * 业务对象附件零自建表——上传时带 owner 三元组落 sys_file，按三元组列出；
 * 下载签名 URL 经业务模块注册的 FilePermissionChecker 鉴权。
 */

/** 业务对象归属三元组（与 sys_file.owner_* 列一致）。 */
export type BizFileOwner = {
  ownerModule: string;
  ownerRefType: string;
  /** 业务对象 id（雪花 id，字符串）。 */
  ownerRefId: string;
};

/** sys_file 元数据视图（后端 FileMetadata；不含后端凭证）。 */
export type BizFileMetadata = {
  fileId: string;
  tenantId: string;
  fileName: string;
  mimeType?: string | null;
  sizeBytes: number;
  visibility: string;
  ownerModule?: string | null;
  ownerRefType?: string | null;
  ownerRefId?: string | null;
  uploadedBy?: string | null;
  uploadedAt?: string | null;
};

export function bizFileApi(http: HttpClient) {
  const base = '/api/v1/files';
  return {
    /** 按业务对象列出附件（活跃记录，上传时间倒序）。 */
    async list(owner: BizFileOwner): Promise<BizFileMetadata[]> {
      const res = await http.get<BizFileMetadata[]>(base, { params: owner });
      return res.data;
    },
    /** Multipart 直传（≤100MB），visibility 固定 business-only。 */
    async upload(file: File, owner: BizFileOwner): Promise<FileUploadResult> {
      const form = new FormData();
      form.append('file', file);
      const res = await http.post<FileUploadResult>(`${base}/upload`, form, {
        params: { visibility: 'business-only', ...owner },
      });
      return res.data;
    },
    /** 签名下载 URL（5 分钟有效；business-only 经 FilePermissionChecker 鉴权）。 */
    async downloadUrl(fileId: string): Promise<string> {
      const res = await http.get<{ url: string }>(`${base}/${encodeURIComponent(fileId)}/url`);
      return res.data.url;
    },
    /** 软删附件。 */
    async remove(fileId: string): Promise<void> {
      await http.delete(`${base}/${encodeURIComponent(fileId)}`);
    },
  };
}
