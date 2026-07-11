package com.wensaas.core.api.ops.health;

import java.time.Instant;
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
 * 运营后台 · 客户健康度。
 *
 * <p>纯信息看板：总览三档计数 + 租户健康度列表 + 单租户详情。读路径全部按当前运营
 * {@code OpsAccessContext.dataScope} 过滤（{@code OPS_OWNED} 仅看 {@code csm_user_id=self} 的租户）。
 *
 * <p>评分每天凌晨由 {@code TenantHealthScoreJob} 计算落 {@code sys_tenant_health}；
 * 列表/总览读快照（快、稳）；详情实时重算风险信号（信号从底层数据实时取）。
 */
public interface OpsHealthService {

    /** 总览：健康/关注/危险三档计数 + 相对上次计算的环比增减。 */
    OverviewMetrics overview();

    /** 健康度列表（可按分档过滤 + 关键词搜租户名/编号）。读 {@code sys_tenant_health} 快照。 */
    HealthPage list(HealthLevel level, String keyword, int page, int pageSize);

    /**
     * 单租户健康度详情：实时重算评分/风险信号/最近 30 天指标。
     *
     * @throws IllegalArgumentException 租户不存在或当前运营无权查看（不在 dataScope 内）
     */
    HealthDetail detail(long tenantId);

    /**
     * 为所有（在库未删除的）租户重算并 upsert 健康度快照，返回危险档租户摘要（供每日告警）。
     * 由 {@code TenantHealthScoreJob} 调度，无视 dataScope（全量）。
     */
    RecomputeResult recomputeAll();

    // ===================== 结果 records =====================

    /** 命中的单个风险信号。weight 为正数扣分幅度。 */
    record RiskSignal(String code, String label, int weight) {}

    /** 最近 30 天指标快照（详情页展示；无数据源的维度为 null）。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class HealthMetrics {
        private Integer monthlyActiveMembers;
        private Integer totalMembers;
        private Long moduleAccessCount;
        private Integer moduleAccessChangePct;
        private Long dataGrowth;
        private Integer ticketCount;
    }

    /** 总览三档卡片：counts + 环比 delta（当前档 - 上次同档数量）。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class OverviewMetrics {
        private long healthy;
        private long healthyDelta;
        private long warning;
        private long warningDelta;
        private long danger;
        private long dangerDelta;
    }

    /** 列表项（读快照）。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class HealthListItem {
        private long tenantId;
        private String tenantName;
        private String tenantNo;
        private int score;
        private HealthLevel level;
        private List<RiskSignal> riskSignals;
        private Instant computedAt;
    }

    /** 列表分页结果。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class HealthPage {
        private List<HealthListItem> records;
        private long total;
        private int page;
        private int pageSize;
    }

    /** 详情（实时重算）。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class HealthDetail {
        private long tenantId;
        private String tenantName;
        private String tenantNo;
        private int score;
        private HealthLevel level;
        private List<RiskSignal> riskSignals;
        private HealthMetrics metrics;
        private Instant expireAt;
        private Instant computedAt;
    }

    /** 危险租户摘要项（每日告警用）。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class DangerBrief {
        private long tenantId;
        private String tenantName;
        private List<RiskSignal> riskSignals;
    }

    /** 全量重算结果。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class RecomputeResult {
        private int total;
        private int healthy;
        private int warning;
        private int danger;
        private List<DangerBrief> dangerList;
    }
}
