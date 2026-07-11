import type { HttpClient } from './http';

/**
 * 免登录文件分享落地页（FC-901）对外只读视图。
 * 对应后端 `/api/v1/public/shares/**`（无鉴权、跨租户，token 自携租户归属）。
 * 字段较内部 FileShareInfo 收窄：不含 createdBy / downloadCount 等内部信息。
 */
export type PublicShareView = {
  shareToken: string;
  fileName: string;
  mimeType?: string | null;
  sizeBytes?: number | null;
  expiresAt?: string | null;
  hasPassword: boolean;
  maxDownloads?: number | null;
  remainingDownloads?: number | null;
};

/** 消费分享成功后返回的签名下载 URL（5 分钟过期）。 */
export type PublicShareConsumeResult = {
  signedDownloadUrl: string;
  expiresAt?: string | null;
};

export function publicShareApi(http: HttpClient) {
  const base = '/api/v1/public/shares';
  return {
    /** 查看分享快照（不消耗次数）。失败：404 不存在 / 410 过期或撤销。 */
    async getShare(shareToken: string): Promise<PublicShareView> {
      const res = await http.get<PublicShareView>(`${base}/${encodeURIComponent(shareToken)}`);
      return res.data;
    },
    /**
     * 消费分享并拿签名下载 URL。
     * 失败：401 需密码 / 403 密码错 / 410 过期或撤销 / 429 超限 / 404 不存在。
     */
    async download(shareToken: string, password?: string): Promise<PublicShareConsumeResult> {
      const res = await http.post<PublicShareConsumeResult>(
        `${base}/${encodeURIComponent(shareToken)}/download`,
        password ? { password } : {},
      );
      return res.data;
    },
  };
}
