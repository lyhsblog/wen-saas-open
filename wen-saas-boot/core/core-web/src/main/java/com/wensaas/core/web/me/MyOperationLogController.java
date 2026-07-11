package com.wensaas.core.web.me;

import com.wensaas.core.api.auth.user.UserContext;
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
 * 当前用户 · 我的操作记录（成员看自己，UX3）。
 *
 * <p>无需特殊权限，任意已登录成员可看；复用 {@link TenantOperationLogQueryService} 并强制
 * {@code restrictOperatorId = 当前用户}，叠加租户线拦截器的租户隔离，越权不可见。
 */
@Tag(name = "me-operation-log", description = "我的操作记录（成员看自己）")
@SecurityRequirement(name = "bearer-jwt")
@RestController
@RequestMapping("/api/v1/me/operation-logs")
public class MyOperationLogController {

    private final TenantOperationLogQueryService service;

    public MyOperationLogController(TenantOperationLogQueryService service) {
        this.service = service;
    }

    @Operation(summary = "分页查询（仅本人）")
    @GetMapping
    public TenantOperationLogQueryService.OpLogPage list(
            @Valid @ParameterObject PageQuery pageQuery,
            @Parameter(description = "时间起 ISO-8601") @RequestParam(value = "startAt", required = false) Instant startAt,
            @Parameter(description = "时间止") @RequestParam(value = "endAt", required = false) Instant endAt,
            @Parameter(description = "操作类型 action") @RequestParam(value = "action", required = false) String action,
            @Parameter(description = "对象类型 objectType") @RequestParam(value = "objectType", required = false) String objectType) {
        long self = UserContext.require().userId();
        return service.list(
                new OpLogQuery(pageQuery.page(), pageQuery.size(), startAt, endAt, self, action, objectType, null, null), self);
    }

    @Operation(summary = "详情（分区表必须带 createdAt；越权不可见）")
    @GetMapping("/{id}")
    public TenantOperationLogQueryService.OpLogDetail get(
            @PathVariable long id,
            @Parameter(description = "记录的 created_at（ISO-8601）", required = true)
            @RequestParam("createdAt") Instant createdAt) {
        long self = UserContext.require().userId();
        return service.getById(id, createdAt, self);
    }
}
