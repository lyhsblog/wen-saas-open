import type { HttpClient } from './http';

/**
 * 业务数据导出框架。`/api/v1/exports`。
 *
 * 发起后按合规策略分流：无需审批回 `submitted + taskId`（进度 / 下载走任务中心
 * `/api/v1/tasks/{id}`）；需审批回 `pending_approval + requestId`（审批通过后自动以
 * 申请人身份投递任务）。雪花 id JSON 序列化为字符串，前端按 string 处理。
 */

/** 可导出对象（当前用户持发起权限的；requiresApproval=true 时发起必填 reason）。 */
export type ExportObjectOption = {
  code: string;
  name: string;
  module: string | null;
  requiresApproval: boolean;
};

export type SubmitExportBody = {
  objectCode: string;
  /** 筛选条件（provider 自定义键，与列表页筛选一致；随任务/审批台账快照透传）。 */
  filters?: Record<string, string>;
  /** 筛选条件人类可读摘要（审批台账展示）。 */
  filterSummary?: string;
  /** 申请理由（命中审批开关时必填，缺失后端 422 REASON_REQUIRED）。 */
  reason?: string;
};

export type SubmitExportResult = {
  status: 'submitted' | 'pending_approval';
  taskId: string | null;
  requestId: string | null;
};

export function exportsApi(http: HttpClient) {
  const base = '/api/v1/exports';
  return {
    async objects(): Promise<ExportObjectOption[]> {
      const res = await http.get<ExportObjectOption[]>(`${base}/objects`);
      return res.data;
    },
    async submit(body: SubmitExportBody): Promise<SubmitExportResult> {
      const res = await http.post<SubmitExportResult>(base, body);
      return res.data;
    },
  };
}
