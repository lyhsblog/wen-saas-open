package com.wensaas.core.web.tenant;

import com.wensaas.core.api.auth.permission.DataPermission;
import com.wensaas.core.api.auth.permission.RequirePermission;
import com.wensaas.core.api.tenant.audit.TenantOperationLogQueryService;
import com.wensaas.core.api.tenant.audit.TenantOperationLogQueryService.OpLogQuery;
import com.wensaas.core.web.common.PageQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * 租户后台 · 审计日志（看本租户全员的操作审计）。
 *
 * <p>门控 {@code audit.view}（主管理员恒有，可赋合规 / 安全等角色）。数据源 {@code sys_operation_log}，
 * 租户隔离由租户线拦截器自动注入 {@code tenant_id = 当前租户} 保证。
 */
@Tag(name = "tenant-audit-log", description = "租户审计日志（看全租户操作审计）")
@SecurityRequirement(name = "bearer-jwt")
@RestController
@RequestMapping("/api/v1/base/audit-logs")
public class TenantAuditLogController {

    private final TenantOperationLogQueryService service;

    public TenantAuditLogController(TenantOperationLogQueryService service) {
        this.service = service;
    }

    @Operation(summary = "分页查询（本租户全员）")
    @DataPermission("audit.view")
    @RequirePermission("audit.view")
    @GetMapping
    public TenantOperationLogQueryService.OpLogPage list(
            @Valid @ParameterObject PageQuery pageQuery,
            @Parameter(description = "时间起 ISO-8601") @RequestParam(value = "startAt", required = false) Instant startAt,
            @Parameter(description = "时间止") @RequestParam(value = "endAt", required = false) Instant endAt,
            @Parameter(description = "操作人 ID") @RequestParam(value = "operatorId", required = false) Long operatorId,
            @Parameter(description = "操作类型 action") @RequestParam(value = "action", required = false) String action,
            @Parameter(description = "对象类型 objectType（中文，匹配 object_type 列）") @RequestParam(value = "objectType", required = false) String objectType,
            @Parameter(description = "Request ID") @RequestParam(value = "requestId", required = false) String requestId,
            @Parameter(description = "对象 ID（精确匹配 object_id）") @RequestParam(value = "objectId", required = false) String objectId) {
        return service.list(
                new OpLogQuery(pageQuery.page(), pageQuery.size(), startAt, endAt, operatorId, action, objectType, requestId, objectId),
                null);
    }

    @Operation(summary = "详情（分区表必须带 createdAt 才能命中单分区）")
    @DataPermission("audit.view")
    @RequirePermission("audit.view")
    @GetMapping("/{id}")
    public TenantOperationLogQueryService.OpLogDetail get(
            @PathVariable long id,
            @Parameter(description = "记录的 created_at（ISO-8601）", required = true)
            @RequestParam("createdAt") Instant createdAt) {
        return service.getById(id, createdAt, null);
    }
}
