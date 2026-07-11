import { formatDateTime, type TaskItem, type TaskStatus } from '@wen-saas/shared-ui';
import type { TaskView } from '@wen-saas/domain';

/** 后端状态（小写枚举名）→ shared-ui TaskCenter 状态。 */
function toStatus(status: string | undefined): TaskStatus {
  switch (status) {
    case 'running':
    case 'cancelling':
      return 'running';
    case 'completed':
      return 'done';
    case 'failed':
      return 'failed';
    case 'cancelled':
      return 'canceled';
    default:
      return 'pending';
  }
}

function formatFinish(iso: string | undefined): string | undefined {
  if (!iso) return undefined;
  const formatted = formatDateTime(iso);
  return formatted === '—' ? undefined : formatted;
}

/** 完成 / 失败任务的副标题结果文案。 */
function resultText(t: TaskView): string | undefined {
  if (t.status === 'completed') {
    const total = t.totalCount ?? t.successCount ?? 0;
    const failed = t.failedCount ?? 0;
    return failed > 0
      ? `成功 ${t.successCount ?? 0} / 失败 ${failed}（共 ${total}）`
      : `成功 ${t.successCount ?? total} 条`;
  }
  if (t.status === 'failed') {
    return t.errorMessage || '执行失败';
  }
  return undefined;
}

/** 结果是否带可下载文件（resultData.fileId 存在）。 */
export function hasResultFile(t: TaskView): boolean {
  const data = t.resultData as Record<string, unknown> | undefined;
  return data != null && data.fileId != null;
}

export function toTaskItem(t: TaskView): TaskItem {
  const status = toStatus(t.status);
  const item: TaskItem = {
    id: String(t.id ?? ''),
    kind: (t.type as TaskItem['kind']) ?? 'other',
    name: t.name ?? '未命名任务',
    status,
  };
  if (status === 'running') {
    item.progress = t.progress ?? 0;
  } else {
    const finish = formatFinish(t.finishedAt ?? t.createdAt);
    if (finish != null) item.finish = finish;
  }
  const result = resultText(t);
  if (result != null) item.result = result;
  return item;
}
