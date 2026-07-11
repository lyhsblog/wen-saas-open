import { formatDateTime, toneBadge } from '@wen-saas/shared-ui';
import type { SemanticTone, StatusBadge } from '@wen-saas/shared-ui';
import type { DataExportMode, DataExportStatus, ExportRequestStatus } from '@wen-saas/api-client';

/** 审批状态 → 中文标签 + 语义 tone（渲染走 StatusPill）。 */
export const STATUS_META: Record<ExportRequestStatus, { label: string; tone: SemanticTone }> = {
  PENDING: { label: '审批中', tone: 'warning' },
  APPROVED: { label: '已批准', tone: 'success' },
  REJECTED: { label: '已拒绝', tone: 'danger' },
};

/** 全量导出状态 → 中文标签 + 语义 tone。 */
export const DATA_EXPORT_STATUS_META: Record<
  DataExportStatus,
  { label: string; tone: SemanticTone }
> = {
  PACKAGING: { label: '打包中', tone: 'info' },
  COMPLETED: { label: '已完成', tone: 'success' },
  FAILED: { label: '失败', tone: 'danger' },
};

/** 敏感字段处理方式 → 中文标签 + 语义 tone（完整=含敏感明文，用 warning 提示风险）。 */
export const DATA_MODE_META: Record<DataExportMode, { label: string; tone: SemanticTone }> = {
  FULL: { label: '完整', tone: 'warning' },
  MASKED: { label: '脱敏', tone: 'info' },
};

/** 审批状态 → StatusPill badge。 */
export function exportRequestStatusBadge(s: ExportRequestStatus): StatusBadge {
  return toneBadge(STATUS_META[s].tone, STATUS_META[s].label);
}

/** 全量导出状态 → StatusPill badge。 */
export function dataExportStatusBadge(s: DataExportStatus): StatusBadge {
  return toneBadge(DATA_EXPORT_STATUS_META[s].tone, DATA_EXPORT_STATUS_META[s].label);
}

/** 敏感字段处理方式 → StatusPill badge。 */
export function dataModeBadge(m: DataExportMode): StatusBadge {
  return toneBadge(DATA_MODE_META[m].tone, DATA_MODE_META[m].label);
}

/** 字节数 → 人类可读（KB/MB/GB）。 */
export function fmtBytes(n: number | null | undefined): string {
  if (n == null) return '—';
  if (n < 1024) return `${n} B`;
  const units = ['KB', 'MB', 'GB', 'TB'];
  let v = n / 1024;
  let i = 0;
  while (v >= 1024 && i < units.length - 1) {
    v /= 1024;
    i += 1;
  }
  return `${v.toFixed(1)} ${units[i]}`;
}

/** ISO-8601 字符串 → 本地化时间；空值占位 —。 */
export function fmtTime(v: string | null | undefined): string {
  return formatDateTime(v);
}
