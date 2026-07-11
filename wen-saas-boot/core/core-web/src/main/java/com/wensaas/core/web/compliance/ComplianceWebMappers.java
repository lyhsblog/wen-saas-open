package com.wensaas.core.web.compliance;

import com.wensaas.core.api.compliance.export.DataExportPage;
import com.wensaas.core.api.compliance.export.DataExportView;
import com.wensaas.core.api.compliance.export.ExportRequestPage;
import com.wensaas.core.api.compliance.export.ExportRequestView;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/** 合规 api 视图 → Web 视图映射（时间格式化为北京时间 ISO-8601）。 */
final class ComplianceWebMappers {

    private ComplianceWebMappers() {
    }

    static ExportRequestWebView toWeb(ExportRequestView v) {
        return new ExportRequestWebView(
                v.id(), v.requestNo(), v.tenantId(), v.tenantName(),
                v.objectType(), v.objectCode(), v.filterSummary(),
                v.estimatedCount(),
                v.reason(), v.status(),
                v.applicantId(), v.applicantName(), v.applicantDeptName(),
                v.approverId(), v.approverName(), v.approvalComment(),
                formatBeijing(v.appliedAt()), formatBeijing(v.decidedAt()), v.taskId());
    }

    static ExportRequestPageWebView toWeb(ExportRequestPage p) {
        return new ExportRequestPageWebView(
                p.items().stream().map(ComplianceWebMappers::toWeb).toList(),
                p.total(), p.page(), p.size());
    }

    static DataExportWebView toWeb(DataExportView v) {
        return new DataExportWebView(
                v.id(), v.requestNo(), v.tenantId(), v.reason(),
                v.dataMode(), v.scopeSummary(), v.status(), v.itemSummary(),
                v.fileName(), v.fileSize(), v.downloadToken(), formatBeijing(v.expiresAt()),
                v.applicantId(), v.applicantName(),
                formatBeijing(v.requestedAt()), formatBeijing(v.completedAt()), v.errorMessage());
    }

    static DataExportPageWebView toWeb(DataExportPage p) {
        return new DataExportPageWebView(
                p.items().stream().map(ComplianceWebMappers::toWeb).toList(),
                p.total(), p.page(), p.size());
    }

    static String formatBeijing(Instant t) {
        if (t == null) {
            return null;
        }
        return t.atOffset(ZoneOffset.of("+08:00")).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
}
