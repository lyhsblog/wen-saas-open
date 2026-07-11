package com.wensaas.core.web.ops;

import com.wensaas.core.api.auth.permission.RequirePermission;
import com.wensaas.core.api.ops.audit.OpsOperationLogQueryService;
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
 * 运营后台 · 操作日志查询。
 *
 * <p>数据源：{@code sys_operation_log WHERE tenant_id=0}（运营产生）。OPS_OWNED 时仅看自己产生的。
 */
@Tag(name = "ops-operation-log", description = "运营操作日志查询")
@SecurityRequirement(name = "bearer-jwt")
@RestController
@RequestMapping("/api/v1/ops/operation-logs")
public class OpsOperationLogController {

    private final OpsOperationLogQueryService service;

    public OpsOperationLogController(OpsOperationLogQueryService service) {
        this.service = service;
    }

    @Operation(summary = "分页查询")
    @RequirePermission("ops.operation_log.list")
    @GetMapping
    public OpsOperationLogQueryService.OpLogPage list(
            @Valid @ParameterObject PageQuery pageQuery,
            @Parameter(description = "时间起 ISO-8601") @RequestParam(value = "startAt", required = false) Instant startAt,
            @Parameter(description = "时间止") @RequestParam(value = "endAt", required = false) Instant endAt,
            @Parameter(description = "操作人 ID") @RequestParam(value = "operatorId", required = false) Long operatorId,
            @Parameter(description = "操作类型 action") @RequestParam(value = "action", required = false) String action,
            @Parameter(description = "目标租户 ID") @RequestParam(value = "targetTenantId", required = false) Long targetTenantId,
            @Parameter(description = "Request ID") @RequestParam(value = "requestId", required = false) String requestId) {
        return service.list(new OpsOperationLogQueryService.OpLogQuery(
                pageQuery.page(), pageQuery.size(), startAt, endAt, operatorId, action, targetTenantId, requestId,
                null, null, null));
    }

    @Operation(summary = "详情（分区表必须带 createdAt 才能命中单分区）")
    @RequirePermission("ops.operation_log.list")
    @GetMapping("/{id}")
    public OpsOperationLogQueryService.OpLogDetail get(
            @PathVariable long id,
            @Parameter(description = "记录的 created_at（ISO-8601）", required = true)
            @RequestParam("createdAt") Instant createdAt) {
        return service.getById(id, createdAt);
    }
}
