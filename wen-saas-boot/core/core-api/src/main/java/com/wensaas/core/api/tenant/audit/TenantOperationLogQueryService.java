package com.wensaas.core.api.tenant.audit;

import com.wensaas.core.api.ops.audit.OpsOperationLogQueryService.DataChange;

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
 * 租户侧操作审计日志查询（UX3）。
 *
 * <p>数据源 {@code sys_operation_log}（混合表 + 月分区）。租户隔离由 MyBatis-Plus 租户线拦截器
 * 自动注入 {@code tenant_id = 当前租户} 保证（该表不在 platform-only 白名单）。两种视角共用本服务、
 * 以 {@code restrictOperatorId} 区分：
 * <ul>
 *   <li><b>审计日志</b>（管理员看全租户）：{@code restrictOperatorId=null}，端点门控 {@code audit.view}；</li>
 *   <li><b>我的操作记录</b>（成员看自己）：{@code restrictOperatorId=当前用户}，任意已登录成员可看自己。</li>
 * </ul>
 *
 * <p>{@code DataChange / FieldChange}（③ 字段级变更）复用 {@code OpsOperationLogQueryService} 的同名
 * 中性审计值对象（由 {@code AuditChangeRenderer} 渲染成句），避免重复定义。
 */
public interface TenantOperationLogQueryService {

    /** 分页查询；{@code restrictOperatorId} 非空时仅返回该操作人的记录（我的操作记录）。 */
    OpLogPage list(OpLogQuery query, Long restrictOperatorId);

    /**
     * 详情（含 ③ 数据变更日志，按 requestId 关联）。分区表必须带 {@code createdAt} 才能命中单分区；
     * {@code restrictOperatorId} 非空时强制校验归属，越权 / 未命中返回 {@code null}。
     */
    OpLogDetail getById(long id, Instant createdAt, Long restrictOperatorId);

    record OpLogQuery(
            int page,
            int size,
            Instant startAt,
            Instant endAt,
            Long operatorId,
            String action,
            String objectType,
            String requestId,
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
        private String objectId;
        private String objectDesc;
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
        private String objectId;
        private String objectDesc;
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
}
