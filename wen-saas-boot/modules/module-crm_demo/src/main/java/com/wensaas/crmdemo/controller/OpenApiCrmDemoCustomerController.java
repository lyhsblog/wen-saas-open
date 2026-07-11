package com.wensaas.crmdemo.controller;

import com.wensaas.core.api.idempotent.IdempotencyGuard;
import com.wensaas.core.api.tenant.TenantContext;
import com.wensaas.crmdemo.service.CrmDemoCustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 开放 API · 客户（AppKey 鉴权，外部系统对接）。
 *
 * <p>鉴权 / 验签 / IP 白名单 / 限流全部由网关过滤器完成，进到这里即已认证为某租户的
 * API 凭证；<b>不挂 {@code @RequirePermission} / {@code @DataPermission}</b>——凭证是租户级
 * 机器身份（数据权限切面对 byApiKey 主体放行），能拿到 key 即等于被授权访问本租户开放
 * 数据全量，粒度控制靠凭证的启停 / IP 白名单 / 限流。
 *
 * <p>建档要求 {@code Idempotency-Key} 头（调用方自造的唯一键）：网络重试重复提交时
 * 第二次直接 409，防止重复建档。webhook 订阅方拿到事件里的 customerId 后也可回查本接口。
 */
@Tag(name = "openapi-crm-demo-customer", description = "开放 API · 客户（AppKey 鉴权）")
@RestController
@RequestMapping("/api/v1/openapi/crm_demo/customers")
public class OpenApiCrmDemoCustomerController {

    /** 幂等键去重域（按租户隔离拼进 id）与保留时长。 */
    private static final String IDEMPOTENCY_SCOPE = "openapi-crm-customer-create";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

    private final CrmDemoCustomerService service;
    private final IdempotencyGuard idempotency;

    public OpenApiCrmDemoCustomerController(CrmDemoCustomerService service, IdempotencyGuard idempotency) {
        this.service = service;
        this.idempotency = idempotency;
    }

    @Operation(summary = "客户列表", description = "本租户全量（无行级过滤）；分页 + 阶段/来源/关键字筛选")
    @GetMapping
    public CrmDemoCustomerService.PageResult list(
            @Parameter(description = "阶段 LEAD/FOLLOWING/WON/LOST") @RequestParam(required = false) String status,
            @Parameter(description = "名称/编号关键字") @RequestParam(required = false) String keyword,
            @Parameter(description = "来源字典项 code") @RequestParam(required = false) String source,
            @RequestParam(required = false, defaultValue = "1") long page,
            @RequestParam(required = false, defaultValue = "20") long size) {
        return service.page(new CrmDemoCustomerService.Query(
                status, keyword, null, source, Map.of(), page, Math.min(size, 200)));
    }

    @Operation(summary = "客户详情", description = "含自定义字段视图（enum/ref 带 __label）")
    @GetMapping("/{id}")
    public CrmDemoCustomerService.Detail get(@PathVariable Long id) {
        return service.get(id);
    }

    @Operation(summary = "建档客户",
            description = "必带 Idempotency-Key 头（调用方唯一键，保留 24h）：重复提交返回 409，"
                    + "调用方按首个成功响应为准。负责人缺省为凭证签发人。")
    @PostMapping
    public CrmDemoCustomerService.Brief create(
            @RequestHeader(name = "Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateRequest req) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少 Idempotency-Key 头");
        }
        String dedupId = TenantContext.require() + ":" + idempotencyKey.trim();
        AtomicReference<CrmDemoCustomerService.Brief> out = new AtomicReference<>();
        boolean executed = idempotency.runOnce(IDEMPOTENCY_SCOPE, dedupId, IDEMPOTENCY_TTL,
                () -> out.set(service.create(new CrmDemoCustomerService.CreateCommand(
                        req.name(), req.code(), req.status(), req.source(),
                        req.ownerId(), req.ownerDeptId(), req.custom()))));
        if (!executed) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "重复的 Idempotency-Key：该请求已受理过，请以首次响应为准");
        }
        return out.get();
    }

    // ============================== 请求 DTO ==============================

    public record CreateRequest(
            @NotBlank(message = "客户名称必填") String name,
            String code,
            String status,
            String source,
            Long ownerId,
            Long ownerDeptId,
            Map<String, Object> custom) {
    }
}
