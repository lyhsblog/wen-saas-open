package com.wensaas.core.api.compliance.export;

import java.time.Instant;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * 全量数据导出申请视图（列表项 / 详情共用）。
 *
 * <p>{@code downloadToken} 为下载令牌占位（待文件中心接入）；{@code expiresAt} 为下载链接
 * 失效时间（完成后 7 天）。雪花 {@code id}/{@code tenantId}/{@code applicantId} 由 Web/JSON 层
 * 序列化为字符串。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public class DataExportView {
    private long id;
    private String requestNo;
    private long tenantId;
    private String reason;
    private DataExportMode dataMode;
    private String scopeSummary;
    private DataExportStatus status;
    private String itemSummary;
    private String fileName;
    private Long fileSize;
    private String downloadToken;
    private Instant expiresAt;
    private long applicantId;
    private String applicantName;
    private Instant requestedAt;
    private Instant completedAt;
    private String errorMessage;

}
