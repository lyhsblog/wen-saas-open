package com.wensaas.core.web.openapi;

import com.wensaas.core.api.audit.Auditable;
import com.wensaas.core.api.auth.permission.RequirePermission;
import com.wensaas.core.impl.openapi.calllog.ApiCallLogQueryService;
import com.wensaas.core.impl.openapi.key.ApiKeyAdminService;
import com.wensaas.core.web.common.PageQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 租户后台 · 开放 API 凭证（AppKey）管理。
 *
 * <p>secret 仅签发 / 轮换时明文返回一次。凭证不删除、只启停（调用日志按凭证关联可溯）。
 * 开放 API 框架关闭（{@code wen-saas-boot.openapi.enabled=false}）时本控制器不装配。
 */
@Tag(name = "tenant-api-key", description = "租户后台 · 开放 API 凭证管理")
@SecurityRequirement(name = "bearer-jwt")
@RestController
@ConditionalOnProperty(prefix = "wen-saas-boot.openapi", name = "enabled",
        havingValue = "true", matchIfMissing = true)
@RequestMapping("/api/v1/base/api-keys")
public class TenantApiKeyController {

    private final ApiKeyAdminService service;
    private final ApiCallLogQueryService callLogService;

    public TenantApiKeyController(ApiKeyAdminService service, ApiCallLogQueryService callLogService) {
        this.service = service;
        this.callLogService = callLogService;
    }

    @Operation(summary = "凭证列表", description = "本租户全部凭证（含已停用），不含 secret")
    @RequirePermission("api_key.view")
    @GetMapping
    public List<ApiKeyAdminService.ApiKeyView> list() {
        return service.list();
    }

    @Operation(summary = "签发凭证", description = "返回体中的 secret 为明文、仅此一次展示")
    @RequirePermission("api_key.manage")
    @Auditable(module = "openapi", action = "签发API凭证",
            objectIdExpr = "#result.id()", objectDescExpr = "#result.name()")
    @PostMapping
    public ApiKeyAdminService.CreatedApiKey create(@Valid @RequestBody UpsertRequest req) {
        return service.create(req.name(), req.expiresAt(), req.ipWhitelist(),
                req.rateLimitQps(), req.requireSignature());
    }

    @Operation(summary = "编辑凭证", description = "名称 / 有效期 / IP 白名单 / 限流 / 验签开关；不含密钥（走轮换）")
    @RequirePermission("api_key.manage")
    @Auditable(module = "openapi", action = "编辑API凭证",
            objectIdExpr = "#id", objectDescExpr = "#result.name()")
    @PutMapping("/{id}")
    public ApiKeyAdminService.ApiKeyView update(@PathVariable Long id,
                                                @Valid @RequestBody UpsertRequest req) {
        return service.update(id, req.name(), req.expiresAt(), req.ipWhitelist(),
                req.rateLimitQps(), req.requireSignature());
    }

    @Operation(summary = "启用凭证")
    @RequirePermission("api_key.manage")
    @Auditable(module = "openapi", action = "启用API凭证",
            objectIdExpr = "#id", objectDescExpr = "#result.name()")
    @PostMapping("/{id}/enable")
    public ApiKeyAdminService.ApiKeyView enable(@PathVariable Long id) {
        return service.setStatus(id, true);
    }

    @Operation(summary = "停用凭证", description = "即时生效（网关按请求查库）；重新启用可恢复")
    @RequirePermission("api_key.manage")
    @Auditable(module = "openapi", action = "停用API凭证",
            objectIdExpr = "#id", objectDescExpr = "#result.name()")
    @PostMapping("/{id}/disable")
    public ApiKeyAdminService.ApiKeyView disable(@PathVariable Long id) {
        return service.setStatus(id, false);
    }

    @Operation(summary = "轮换密钥", description = "api_key 标识不变、secret 换新（旧值立即失效），新 secret 仅此一次展示")
    @RequirePermission("api_key.manage")
    @Auditable(module = "openapi", action = "轮换API凭证密钥",
            objectIdExpr = "#id", objectDescExpr = "#result.name()")
    @PostMapping("/{id}/rotate-secret")
    public ApiKeyAdminService.CreatedApiKey rotateSecret(@PathVariable Long id) {
        return service.rotateSecret(id);
    }

    @Operation(summary = "调用日志", description = "开放 API 网关调用日志分页（本租户，倒序）；"
            + "可按凭证 / HTTP 状态码 / 时间窗过滤。日志按月分区，带时间窗查询可命中分区裁剪")
    @RequirePermission("api_key.view")
    @GetMapping("/call-logs")
    public ApiCallLogQueryService.LogPage callLogs(
            @Parameter(description = "凭证 id") @RequestParam(required = false) Long apiKeyId,
            @Parameter(description = "HTTP 状态码精确匹配") @RequestParam(required = false) Integer statusCode,
            @Parameter(description = "起始时间（含）") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @Parameter(description = "结束时间（不含）") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @Valid @ParameterObject PageQuery pageQuery) {
        return callLogService.page(apiKeyId, statusCode, from, to, pageQuery.page(), pageQuery.size());
    }

    // ============================== 请求 DTO ==============================

    public record UpsertRequest(
            @NotBlank(message = "名称必填") String name,
            LocalDateTime expiresAt,
            List<String> ipWhitelist,
            Integer rateLimitQps,
            Boolean requireSignature) {
    }
}
