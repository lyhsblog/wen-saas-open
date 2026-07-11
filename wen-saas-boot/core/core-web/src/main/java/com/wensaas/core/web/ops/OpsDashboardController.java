package com.wensaas.core.web.ops;

import com.wensaas.core.api.auth.permission.RequirePermission;
import com.wensaas.core.api.ops.dashboard.OpsDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 运营后台 · 工作台首页。
 *
 * <p>指标 + 待办 + 近期动态均按当前用户 dataScope 过滤：
 * OPS_OWNED 时只看自己归属（csm_user_id=self）的租户对应数据。
 */
@Tag(name = "ops-dashboard", description = "运营工作台首页")
@SecurityRequirement(name = "bearer-jwt")
@RestController
@RequestMapping("/api/v1/ops/dashboard")
public class OpsDashboardController {

    private final OpsDashboardService service;

    public OpsDashboardController(OpsDashboardService service) {
        this.service = service;
    }

    @Operation(summary = "4 张关键指标卡片 + 同比 + 紧急计数")
    @RequirePermission("ops.dashboard.view")
    @GetMapping("/overview")
    public OpsDashboardService.OverviewMetrics overview() {
        return service.overview();
    }

    @Operation(summary = "今日待办（到期租户 + 占位）")
    @RequirePermission("ops.dashboard.view")
    @GetMapping("/todos")
    public List<OpsDashboardService.TodoItem> todos() {
        return service.todos();
    }

    @Operation(summary = "近期动态（运营操作流，倒序前 N 条）")
    @RequirePermission("ops.dashboard.view")
    @GetMapping("/recent-activities")
    public List<OpsDashboardService.RecentActivity> recentActivities(
            @RequestParam(value = "limit", defaultValue = "20") int limit) {
        return service.recentActivities(limit);
    }
}
