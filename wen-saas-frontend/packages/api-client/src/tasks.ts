import type { TaskView, PageResponseTaskView } from '@wen-saas/domain';
import type { HttpClient } from './http';

export type TaskListQuery = {
  /** 状态过滤：pending/running/completed/failed/cancelled... 不传=全部 */
  status?: string;
  /** 类型过滤：import/export/batch/other 不传=全部 */
  type?: string;
  page?: number;
  size?: number;
};

/**
 * 任务中心只读 API。
 *
 * <p>雪花 id 在 JSON 是字符串，故路径参数与回传 id 一律按 string 处理。
 */
export function tasksApi(http: HttpClient) {
  const base = '/api/v1/tasks';
  return {
    async list(query?: TaskListQuery): Promise<PageResponseTaskView> {
      const res = await http.get<PageResponseTaskView>(base, { params: query });
      return res.data;
    },
    async get(id: string): Promise<TaskView> {
      const res = await http.get<TaskView>(`${base}/${encodeURIComponent(id)}`);
      return res.data;
    },
    async cancel(id: string): Promise<void> {
      await http.post(`${base}/${encodeURIComponent(id)}/cancel`);
    },
    /**
     * 拉取结果文件（后端 302 → 文件中心签名 URL，axios 跟随重定向取回字节）。
     * 走带鉴权的 XHR 而非裸 `<a href>`，以携带 JWT 通过 `/download` 端点。
     */
    async downloadResult(id: string): Promise<Blob> {
      const res = await http.get<Blob>(`${base}/${encodeURIComponent(id)}/download`, {
        responseType: 'blob',
      });
      return res.data;
    },
    /** SSE 进度流地址（配合 EventSource / fetch-stream 使用）。 */
    streamUrl(id: string): string {
      return `${base}/${encodeURIComponent(id)}/stream`;
    },
  };
}
