package com.wensaas.core.web.compliance;

import com.wensaas.core.api.compliance.export.ExportRequestStatus;

/**
 * 超量导出审批申请 Web 视图（时间格式化为 ISO-8601 +08:00 字符串）。
 * 雪花 {@code id}/{@code tenantId}/{@code applicantId}/{@code approverId} 由 JSON 层序列化为字符串。
 */
public record ExportRequestWebView(
        Long id,
        String requestNo,
        Long tenantId,
        String tenantName,
        String objectType,
        String objectCode,
        String filterSummary,
        int estimatedCount,
        String reason,
        ExportRequestStatus status,
        Long applicantId,
        String applicantName,
        String applicantDeptName,
        Long approverId,
        String approverName,
        String approvalComment,
        String appliedAt,
        String decidedAt,
        Long taskId) {
}
