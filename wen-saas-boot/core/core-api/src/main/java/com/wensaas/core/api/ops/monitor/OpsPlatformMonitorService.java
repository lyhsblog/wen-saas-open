package com.wensaas.core.api.ops.monitor;

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
 * 运营后台 · 平台运维监控看板。
 *
 * <p>跨模块业务指标看板：实时聚合现有业务表 / 日志表（不建快照、不依赖定时任务）。所有读路径按当前运营
 * {@code OpsAccessContext.dataScope} 过滤（{@code OPS_OWNED} 仅统计 {@code csm_user_id=self} 的租户口径）。
 *
 * <p>数据源：{@code sys_tenant}（租户/套餐/到期）、{@code sys_login_log}（DAU/WAU/MAU）、
 * {@code sys_module_access_log}（各模块调用/月活）、{@code sys_api_call_log} / {@code sys_webhook_log}
 * （API·Webhook）、{@code sys_ticket_ticket}（工单）、{@code sys_file}（资源）、{@code sys_tenant_renewal_log}
 * （续费率/趋势）。
 */
public interface OpsPlatformMonitorService {

    /** 顶部 4 张 KPI 卡（主指标 + 环比 + 近 14 天 spark）+ 紧急到期计数。 */
    Overview overview();

    /**
     * 趋势分析区双轴折线。
     *
     * @param type  active（DAU/WAU）/ tenant（新增/流失）/ api（调用/错误）/ ticket（新建/关闭）
     * @param range 7d / 30d / mtd（本月至今）/ qtr（近 90 天）
     */
    TrendSeries trends(String type, String range);

    /** 业务模块使用情况表（仅真实注册的业务模块；近 30 天口径）。 */
    List<ModuleMetric> moduleMetrics();

    /** 套餐包分布（active 授权按 packageCode 计数）。 */
    List<PlanSlice> planDistribution();

    /** 近 6 月续费率趋势 + 底部三统计。 */
    RenewalTrend renewalTrend();

    /** 资源使用：文件总量 / 存储后端占比 / Top5 租户 / 本期增量。 */
    ResourceUsage resourceUsage();

    /** API · Webhook 概览（近 7 天）。 */
    ApiWebhookOverview apiWebhookOverview();

    // ===================== 结果 records =====================

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class Overview {
        private long activeTenants;
        private long activeTenantsDelta;
        private long dau;
        private long dauDeltaPct;
        private long apiCallsToday;
        private long apiCallsDeltaPct;
        private long openTickets;
        private long openTicketsDelta;
        private List<Long> tenantSpark;
        private List<Long> dauSpark;
        private List<Long> apiSpark;
        private List<Long> ticketSpark;
        private long expiringIn30Days;
        private long expiringIn7Days;
    }

    record TrendSeries(List<String> dates, List<Long> y1, List<Long> y2, String y1Label, String y2Label) {}

    record ModuleMetric(String code, String name, long enabledTenants, long calls, long dau, double growthPct) {}

    record PlanSlice(String packageCode, String packageName, long count) {}

    record RenewalTrend(List<String> months, List<Double> rates,
                        long expiringSoon, long renewedThisMonth, long addonThisMonth) {}

    record ResourceUsage(long totalFiles, long totalSize, long platformSize, long tenantOssSize,
                         List<TenantSize> topTenants, long incrementThisPeriod) {}

    record TenantSize(long tenantId, String tenantName, long size, boolean tenantDeleted) {}

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class ApiWebhookOverview {
        private long apiCalls7d;
        private long apiErrors7d;
        private long webhookPushes7d;
        private double webhookSuccessRate;
        private List<WebhookFailure> topFailures;
    }

    record WebhookFailure(long webhookId, String webhookName, String eventType,
                          long failCount, double successRate) {}
}
