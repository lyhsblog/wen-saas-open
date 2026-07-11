package com.wensaas.core.api.compliance.export;

import java.time.Instant;

/**
 * 超量导出审批申请视图（列表项 / 详情共用）。
 *
 * <p>雪花 {@code id}/{@code tenantId} 由 JSON 层序列化为字符串；{@code tenantName} 仅运营跨租户
 * 视图填充，租户侧为 null。时间字段由 Web 层格式化为 ISO-8601 +08:00 字符串。
 */
public record ExportRequestView(
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
        Instant appliedAt,
        Instant decidedAt,
        Long taskId) {
}
