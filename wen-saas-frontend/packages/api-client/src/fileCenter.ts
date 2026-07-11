import type { HttpClient } from './http';

/** 文件中心空间。 */
export type FileSpace = 'mine' | 'shared';

export type FolderView = {
  /** 雪花 id，序列化为字符串。 */
  id: string;
  space: string;
  parentId: string | null;
  name: string;
  depth: number;
};

export type FileCenterItem = {
  fileId: string;
  fileName: string;
  mimeType?: string | null;
  sizeBytes: number;
  folderId: string | null;
  uploadedBy?: string | null;
  uploadedAt?: string | null;
};

export type FileCenterListView = {
  folders: FolderView[];
  files: FileCenterItem[];
  total: number;
  page: number;
  size: number;
  /** 从根到当前目录（含当前）。 */
  breadcrumb: FolderView[];
};

export type RecycleItem = {
  type: 'FILE' | 'FOLDER';
  /** 文件为 fileId；文件夹为雪花 id 字符串。 */
  id: string;
  name: string;
  sizeBytes: number;
  deletedAt?: string | null;
  purgeAt?: string | null;
};

export type RecycleListView = {
  records: RecycleItem[];
  total: number;
  page: number;
  size: number;
};

export type QuotaView = {
  usedBytes: number;
  capBytes: number | null;
};

export type FileUploadResult = {
  fileId: string;
  fileName: string;
  sizeBytes: number;
  mimeType?: string | null;
  backendType: string;
};

export type FileShareInfo = {
  shareToken: string;
  fileId: string;
  fileName: string;
  mimeType?: string | null;
  sizeBytes?: number | null;
  expiresAt?: string | null;
  hasPassword: boolean;
  maxDownloads?: number | null;
  downloadCount: number;
  remainingDownloads?: number | null;
  status: string;
  createdAt?: string | null;
};

/** 消费分享成功后返回的签名下载 URL（5 分钟过期）。 */
export type FileShareConsumeResult = {
  signedDownloadUrl: string;
  expiresAt?: string | null;
};

export type CreateShareRequest = {
  /** 有效期秒；null/省略 = 永久。 */
  expiresAfterSeconds?: number | null;
  /** 4-6 位密码（可选）。 */
  password?: string;
  /** 最大下载次数（可选）。 */
  maxDownloads?: number | null;
};

export type RecycleCommand = {
  fileIds?: string[];
  folderIds?: string[];
};

export type FileCenterListQuery = {
  folderId?: string | null;
  keyword?: string;
  page?: number;
  size?: number;
};

/**
 * 文件中心 API（/api/v1/file-center/**）。下载签名 URL 复用 /api/v1/files/{id}/url，
 * 链接分享复用 /api/v1/files/{id}/shares。
 */
export function fileCenterApi(http: HttpClient) {
  const base = '/api/v1/file-center';
  const files = '/api/v1/files';
  return {
    async list(space: FileSpace, query: FileCenterListQuery = {}): Promise<FileCenterListView> {
      const res = await http.get<FileCenterListView>(`${base}/folders`, {
        params: {
          space,
          ...(query.folderId ? { folderId: query.folderId } : {}),
          ...(query.keyword ? { keyword: query.keyword } : {}),
          page: query.page ?? 1,
          size: query.size ?? 50,
        },
      });
      return res.data;
    },
    async createFolder(body: {
      space: FileSpace;
      parentId?: string | null;
      name: string;
    }): Promise<FolderView> {
      const res = await http.post<FolderView>(`${base}/folders`, body);
      return res.data;
    },
    async renameFolder(folderId: string, name: string): Promise<FolderView> {
      const res = await http.put<FolderView>(`${base}/folders/${folderId}/rename`, { name });
      return res.data;
    },
    async moveFolder(folderId: string, newParentId: string | null): Promise<void> {
      await http.put(`${base}/folders/${folderId}/move`, { newParentId });
    },
    async deleteFolder(folderId: string): Promise<void> {
      await http.delete(`${base}/folders/${folderId}`);
    },
    async upload(
      file: File,
      space: FileSpace,
      folderId: string | null,
      onProgress?: (percent: number) => void,
    ): Promise<FileUploadResult> {
      const form = new FormData();
      form.append('file', file);
      const res = await http.post<FileUploadResult>(`${base}/files`, form, {
        params: { space, ...(folderId ? { folderId } : {}) },
        onUploadProgress: (e) => {
          if (onProgress && e.total) onProgress(Math.round((e.loaded / e.total) * 100));
        },
      });
      return res.data;
    },
    async renameFile(fileId: string, name: string): Promise<void> {
      await http.put(`${base}/files/${encodeURIComponent(fileId)}/rename`, { name });
    },
    async moveFile(
      fileId: string,
      targetSpace: FileSpace,
      targetFolderId: string | null,
    ): Promise<void> {
      await http.put(`${base}/files/${encodeURIComponent(fileId)}/move`, {
        targetSpace,
        targetFolderId,
      });
    },
    async deleteFile(fileId: string): Promise<void> {
      await http.delete(`${base}/files/${encodeURIComponent(fileId)}`);
    },
    async recycle(space: FileSpace, page = 1, size = 50): Promise<RecycleListView> {
      const res = await http.get<RecycleListView>(`${base}/recycle`, {
        params: { space, page, size },
      });
      return res.data;
    },
    async restore(cmd: RecycleCommand): Promise<void> {
      await http.post(`${base}/recycle/restore`, cmd);
    },
    async purge(cmd: RecycleCommand): Promise<void> {
      await http.delete(`${base}/recycle`, { data: cmd });
    },
    async quota(): Promise<QuotaView> {
      const res = await http.get<QuotaView>(`${base}/quota`);
      return res.data;
    },
    async downloadUrl(fileId: string): Promise<string> {
      const res = await http.get<{ url: string }>(`${files}/${encodeURIComponent(fileId)}/url`);
      return res.data.url;
    },
    async createShare(fileId: string, body: CreateShareRequest): Promise<FileShareInfo> {
      const res = await http.post<FileShareInfo>(
        `${files}/${encodeURIComponent(fileId)}/shares`,
        body,
      );
      return res.data;
    },
    async listShares(fileId: string): Promise<FileShareInfo[]> {
      const res = await http.get<FileShareInfo[]>(`${files}/${encodeURIComponent(fileId)}/shares`);
      return res.data;
    },
    /** 查看分享元信息（不消耗下载次数）。需密码的分享 hasPassword=true 但不返回 URL。 */
    async getShare(shareToken: string): Promise<FileShareInfo> {
      const res = await http.get<FileShareInfo>(
        `${files}/shares/${encodeURIComponent(shareToken)}`,
      );
      return res.data;
    },
    /** 消费分享：校验密码 + 计数自增，返回签名下载 URL。 */
    async consumeShare(shareToken: string, password?: string): Promise<FileShareConsumeResult> {
      const res = await http.post<FileShareConsumeResult>(
        `${files}/shares/${encodeURIComponent(shareToken)}/download`,
        password ? { password } : {},
      );
      return res.data;
    },
    async revokeShare(shareToken: string): Promise<void> {
      await http.delete(`${files}/shares/${encodeURIComponent(shareToken)}`);
    },
  };
}
