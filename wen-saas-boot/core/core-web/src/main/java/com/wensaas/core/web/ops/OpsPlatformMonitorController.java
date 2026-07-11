package com.wensaas.core.web.ops;

import com.wensaas.core.api.auth.permission.RequirePermission;
import com.wensaas.core.api.ops.monitor.OpsPlatformMonitorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 运营后台 · 平台运维监控看板。
 *
 * <p>跨模块业务指标看板：KPI / 趋势 / 模块使用 / 套餐分布 / 续费率 / 资源 / API·Webhook。
 * 全部实时聚合、按当前运营 {@code dataScope} 收敛、{@code ops.monitor.view} 权限门控。
 */
@Tag(name = "ops-platform-monitor", description = "运营平台运维监控看板")
@SecurityRequirement(name = "bearer-jwt")
@RestController
@RequestMapping("/api/v1/ops/platform")
public class OpsPlatformMonitorController {

    private final OpsPlatformMonitorService service;

    public OpsPlatformMonitorController(OpsPlatformMonitorService service) {
        this.service = service;
    }

    @Operation(summary = "顶部 4 KPI 卡（主指标 + 环比 + 近 14 天 spark）+ 紧急到期计数")
    @RequirePermission("ops.monitor.view")
    @GetMapping("/overview")
    public OpsPlatformMonitorService.Overview overview() {
        return service.overview();
    }

    @Operation(summary = "趋势分析区双轴折线（type=active|tenant|api|ticket, range=7d|30d|mtd|qtr）")
    @RequirePermission("ops.monitor.view")
    @GetMapping("/trends")
    public OpsPlatformMonitorService.TrendSeries trends(
            @RequestParam(value = "type", defaultValue = "active") String type,
            @RequestParam(value = "range", defaultValue = "30d") String range) {
        return service.trends(type, range);
    }

    @Operation(summary = "业务模块使用情况（真实注册模块，近 30 天调用/月活/增长）")
    @RequirePermission("ops.monitor.view")
    @GetMapping("/modules/metrics")
    public List<OpsPlatformMonitorService.ModuleMetric> moduleMetrics() {
        return service.moduleMetrics();
    }

    @Operation(summary = "套餐包分布（active 授权按 packageCode 计数）")
    @RequirePermission("ops.monitor.view")
    @GetMapping("/plans/distribution")
    public List<OpsPlatformMonitorService.PlanSlice> planDistribution() {
        return service.planDistribution();
    }

    @Operation(summary = "近 6 月续费率趋势 + 底部三统计")
    @RequirePermission("ops.monitor.view")
    @GetMapping("/renewals/trend")
    public OpsPlatformMonitorService.RenewalTrend renewalTrend() {
        return service.renewalTrend();
    }

    @Operation(summary = "资源使用：文件总量 / 存储后端占比 / Top5 租户 / 本期增量")
    @RequirePermission("ops.monitor.view")
    @GetMapping("/resources/usage")
    public OpsPlatformMonitorService.ResourceUsage resourceUsage() {
        return service.resourceUsage();
    }

    @Operation(summary = "API · Webhook 概览（近 7 天）")
    @RequirePermission("ops.monitor.view")
    @GetMapping("/api-webhook/overview")
    public OpsPlatformMonitorService.ApiWebhookOverview apiWebhookOverview() {
        return service.apiWebhookOverview();
    }
}
