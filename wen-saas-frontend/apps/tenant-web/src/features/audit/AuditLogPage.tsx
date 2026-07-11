import { OperationLogView } from './OperationLogView';
import { ExportEntryButton } from '../taskCenter/ExportEntryButton';

/** 租户后台 · 审计日志（系统管理；管理员看本租户全员操作审计，门控 audit.view）。 */
export function AuditLogPage() {
  return (
    <OperationLogView
      scope="tenant"
      title="审计日志"
      hint="本租户全员的操作审计记录"
      actions={<ExportEntryButton objectCode="audit_log" size="middle" filterSummary="全部" />}
    />
  );
}
