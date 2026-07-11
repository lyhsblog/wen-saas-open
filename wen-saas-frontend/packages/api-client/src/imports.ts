import type { ImportEntityView, ParseResultView, ImportConfirmView } from '@wen-saas/domain';
import type { HttpClient } from './http';

/** 预览筛选：全部 / 仅通过 / 仅错误。 */
export type ImportPreviewFilter = 'all' | 'passed' | 'error';

/**
 * 数据导入框架 API。
 *
 * <p>解析 / 预览同步返回；执行进度与结果下载复用任务中心 `tasksApi`（confirm 返回 taskId）。
 */
export function importsApi(http: HttpClient) {
  const base = '/api/v1/imports';
  return {
    async listEntities(): Promise<ImportEntityView[]> {
      const res = await http.get<ImportEntityView[]>(`${base}/entities`);
      return res.data;
    },
    /** 下载导入模板 xlsx。 */
    async downloadTemplate(entityCode: string): Promise<Blob> {
      const res = await http.get<Blob>(`${base}/${encodeURIComponent(entityCode)}/template`, {
        responseType: 'blob',
      });
      return res.data;
    },
    /** 上传文件 → 同步解析 + 全量预校验，返回 sessionId + 统计 + 前 200 行预览。 */
    async parse(entityCode: string, file: File): Promise<ParseResultView> {
      const form = new FormData();
      form.append('file', file);
      const res = await http.post<ParseResultView>(
        `${base}/${encodeURIComponent(entityCode)}/parse`,
        form,
      );
      return res.data;
    },
    /** 重取预览（按筛选）。 */
    async getSession(
      sessionId: string,
      filter: ImportPreviewFilter = 'all',
    ): Promise<ParseResultView> {
      const res = await http.get<ParseResultView>(
        `${base}/sessions/${encodeURIComponent(sessionId)}`,
        { params: { filter } },
      );
      return res.data;
    },
    /** 下载错误明细 xlsx。 */
    async downloadErrors(sessionId: string): Promise<Blob> {
      const res = await http.get<Blob>(`${base}/sessions/${encodeURIComponent(sessionId)}/errors`, {
        responseType: 'blob',
      });
      return res.data;
    },
    /** 确认导入（全行通过才允许），返回 taskId；进度 / 结果走任务中心。 */
    async confirm(sessionId: string): Promise<ImportConfirmView> {
      const res = await http.post<ImportConfirmView>(
        `${base}/sessions/${encodeURIComponent(sessionId)}/confirm`,
      );
      return res.data;
    },
  };
}
