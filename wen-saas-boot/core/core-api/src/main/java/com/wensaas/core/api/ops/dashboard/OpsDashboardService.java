package com.wensaas.core.api.ops.dashboard;

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
 * 运营工作台首页聚合。
 *
 * <p>所有方法按当前 {@code OpsAccessContext.dataScope} 过滤：
 * {@code OPS_OWNED} 时仅看 {@code sys_tenant.csm_user_id=self} 的租户对应指标。
 */
public interface OpsDashboardService {

    /** 4 张关键指标卡片 + 同比 + 紧急计数。 */
    OverviewMetrics overview();

    /** 今日待办：到期租户 + 重置密码请求 + 工单（占位）。 */
    List<TodoItem> todos();

    /** 近期动态（运营操作日志倒序前 N 条）。 */
    List<RecentActivity> recentActivities(int limit);

    // ===== records =====

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class OverviewMetrics {
        private long activeTenants;
        private long activeTenantsDeltaVsLastMonth;
        private long newTenantsThisMonth;
        private long renewalsThisMonth;
        private long expiringIn30Days;
        private long expiringIn7Days;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class TodoItem {
        private String type;
        private String title;
        private String summary;
        private String severity;
        private Long targetTenantId;
        private String targetTenantName;
        private String actionUrl;
        private LocalDateTime dueAt;
    }

    record RecentActivity(
            long id,
            long operatorId,
            String operatorName,
            String action,
            String objectDesc,
            Long targetTenantId,
            String targetTenantName,
            LocalDateTime createdAt
    ) {}
}
