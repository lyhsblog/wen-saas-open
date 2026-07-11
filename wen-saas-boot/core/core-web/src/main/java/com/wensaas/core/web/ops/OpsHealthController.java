package com.wensaas.core.web.ops;

import com.wensaas.core.api.auth.permission.RequirePermission;
import com.wensaas.core.api.ops.health.HealthLevel;
import com.wensaas.core.api.ops.health.OpsHealthService;
import com.wensaas.core.web.common.PageQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
 * 运营后台 · 客户健康度。纯信息看板：总览 + 列表 + 详情。
 *
 * <p>读路径按当前运营 {@code dataScope} 过滤；评分每天凌晨由 {@code TenantHealthScoreJob} 计算，
 * 列表/总览读快照，详情实时重算风险信号。
 */
@Tag(name = "ops-health", description = "运营客户健康度")
@SecurityRequirement(name = "bearer-jwt")
@RestController
@RequestMapping("/api/v1/ops/health")
public class OpsHealthController {

    private final OpsHealthService service;

    public OpsHealthController(OpsHealthService service) {
        this.service = service;
    }

    @Operation(summary = "总览：健康/关注/危险三档计数 + 环比增减")
    @RequirePermission("ops.health.view")
    @GetMapping("/overview")
    public OpsHealthService.OverviewMetrics overview() {
        return service.overview();
    }

    @Operation(summary = "健康度列表（可按分档过滤 + 关键词搜租户名/编号）")
    @RequirePermission("ops.health.view")
    @GetMapping("/tenants")
    public HealthListResponse list(
            @RequestParam(value = "level", required = false) String level,
            @RequestParam(value = "keyword", required = false) String keyword,
            @Valid @ParameterObject PageQuery pageQuery) {
        HealthLevel lv = (level == null || level.isBlank()) ? null : HealthLevel.fromDbValue(level);
        OpsHealthService.HealthPage p = service.list(lv, keyword, pageQuery.page(), pageQuery.size());
        return new HealthListResponse(p.records(), p.total());
    }

    @Operation(summary = "单租户健康度详情（实时重算评分/信号/最近30天指标）")
    @RequirePermission("ops.health.view")
    @GetMapping("/tenants/{tenantId}")
    public OpsHealthService.HealthDetail detail(@PathVariable("tenantId") long tenantId) {
        return service.detail(tenantId);
    }

    /** 列表响应（records + total，与其它运营列表口径一致）。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    public static class HealthListResponse {
        private List<OpsHealthService.HealthListItem> records;
        private long total;
    }
}
