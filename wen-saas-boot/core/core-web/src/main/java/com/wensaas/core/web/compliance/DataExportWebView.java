package com.wensaas.core.web.compliance;

import com.wensaas.core.api.compliance.export.DataExportMode;
import com.wensaas.core.api.compliance.export.DataExportStatus;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * 全量数据导出申请 Web 视图（时间格式化为 ISO-8601 +08:00 字符串）。
 * 雪花 {@code id}/{@code tenantId}/{@code applicantId} 由 JSON 层序列化为字符串。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public class DataExportWebView {
    private Long id;
    private String requestNo;
    private Long tenantId;
    private String reason;
    private DataExportMode dataMode;
    private String scopeSummary;
    private DataExportStatus status;
    private String itemSummary;
    private String fileName;
    private Long fileSize;
    private String downloadToken;
    private String expiresAt;
    private Long applicantId;
    private String applicantName;
    private String requestedAt;
    private String completedAt;
    private String errorMessage;

}
