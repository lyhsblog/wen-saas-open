package com.wensaas.core.api.ops.audit;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * 运营操作日志查询。
 *
 * <p>数据源：{@code sys_operation_log WHERE tenant_id=0}（运营产生的全部日志）。
 * 按 {@code OpsAccessContext.dataScope} 进一步过滤：
 * {@code OPS_OWNED} → {@code AND operator_id=:self}。
 */
public interface OpsOperationLogQueryService {

    /** 分页查询。 */
    OpLogPage list(OpLogQuery query);

    /** 详情（含 ③ 数据变更日志，按 requestId 关联）。 */
    OpLogDetail getById(long id, Instant createdAt);

    // ===== records =====

    record OpLogQuery(
            int page,
            int size,
            Instant startAt,
            Instant endAt,
            Long operatorId,
            String action,
            Long targetTenantId,
            String requestId,
            String module,
            String objectType,
            String objectId
    ) {}

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class OpLogPage {
        private List<OpLogListItem> records;
        private long total;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class OpLogListItem {
        private long id;
        private String requestId;
        private long operatorId;
        private String operatorName;
        private String module;
        private String action;
        private String objectType;
        private String objectTypeLabel;
        private String objectId;
        private String objectDesc;
        private Long targetTenantId;
        private String targetTenantName;
        private String method;
        private String path;
        private Integer statusCode;
        private Integer durationMs;
        private String ip;
        private LocalDateTime createdAt;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class OpLogDetail {
        private long id;
        private String requestId;
        private long operatorId;
        private String operatorName;
        private String module;
        private String action;
        private String objectType;
        private String objectTypeLabel;
        private String objectId;
        private String objectDesc;
        private Long targetTenantId;
        private String targetTenantName;
        private String method;
        private String path;
        private Integer statusCode;
        private Integer durationMs;
        private String errorMessage;
        private String ip;
        private String userAgent;
        private LocalDateTime createdAt;
        private List<DataChange> dataChanges;
    }

    /** ③ 一条数据变更记录（一行的逐字段改前/改后，已按 sys_audit_table_config 列字典渲染）。 */
    record DataChange(
            String tableName,
            /** INSERT/UPDATE/DELETE（软删归一为 DELETE）。 */
            String op,
            String rowPk,
            List<FieldChange> fields
    ) {}

    /** 单字段变更（已成句）：{@code label}=中文字段名，{@code before/after}=渲染后的人话（枚举/FK 已译、脱敏列为"已变更"）。 */
    record FieldChange(
            String column,
            String label,
            String before,
            String after
    ) {}
}
