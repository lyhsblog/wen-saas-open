package com.wensaas.core.web.webhook;

import com.wensaas.core.api.audit.Auditable;
import com.wensaas.core.api.auth.permission.RequirePermission;
import com.wensaas.core.impl.webhook.WebhookAdminService;
import com.wensaas.core.web.common.PageQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 租户后台 · Webhook 订阅管理。
 *
 * <p>signing secret 仅创建 / 轮换时明文返回一次；投递日志按订阅分页查询。
 * 推送框架关闭（{@code wen-saas-boot.webhook.enabled=false}）时本控制器不装配。
 */
@Tag(name = "tenant-webhook", description = "租户后台 · Webhook 订阅管理")
@SecurityRequirement(name = "bearer-jwt")
@RestController
@ConditionalOnProperty(prefix = "wen-saas-boot.webhook", name = "enabled",
        havingValue = "true", matchIfMissing = true)   // 与 WebhookConfiguration 同条件（@ConditionalOnBean 对扫描期配置类 bean 不可靠）
@RequestMapping("/api/v1/base/webhooks")
public class TenantWebhookController {

    private final WebhookAdminService service;

    public TenantWebhookController(WebhookAdminService service) {
        this.service = service;
    }

    @Operation(summary = "订阅列表", description = "本租户全部订阅（不含已删除），含连续失败计数")
    @RequirePermission("webhook.view")
    @GetMapping
    public List<WebhookAdminService.WebhookView> list() {
        return service.list();
    }

    @Operation(summary = "新建订阅", description = "返回体中的 signingSecret 为明文、仅此一次展示")
    @RequirePermission("webhook.manage")
    @Auditable(module = "webhook", action = "新建Webhook订阅",
            objectIdExpr = "#result.id()", objectDescExpr = "#result.name()")
    @PostMapping
    public WebhookAdminService.CreatedWebhook create(@Valid @RequestBody UpsertRequest req) {
        return service.create(req.name(), req.targetUrl(), req.subscribedEvents());
    }

    @Operation(summary = "编辑订阅", description = "名称 / 回调地址 / 订阅事件；不含密钥（走轮换）与启停")
    @RequirePermission("webhook.manage")
    @Auditable(module = "webhook", action = "编辑Webhook订阅",
            objectIdExpr = "#id", objectDescExpr = "#result.name()")
    @PutMapping("/{id}")
    public WebhookAdminService.WebhookView update(@PathVariable Long id,
                                                  @Valid @RequestBody UpsertRequest req) {
        return service.update(id, req.name(), req.targetUrl(), req.subscribedEvents());
    }

    @Operation(summary = "启用订阅", description = "重新参与事件分发，连续失败计数清零")
    @RequirePermission("webhook.manage")
    @Auditable(module = "webhook", action = "启用Webhook订阅",
            objectIdExpr = "#id", objectDescExpr = "#result.name()")
    @PostMapping("/{id}/enable")
    public WebhookAdminService.WebhookView enable(@PathVariable Long id) {
        return service.enable(id);
    }

    @Operation(summary = "停用订阅", description = "停止事件分发，配置与日志保留")
    @RequirePermission("webhook.manage")
    @Auditable(module = "webhook", action = "停用Webhook订阅",
            objectIdExpr = "#id", objectDescExpr = "#result.name()")
    @PostMapping("/{id}/disable")
    public WebhookAdminService.WebhookView disable(@PathVariable Long id) {
        return service.disable(id);
    }

    @Operation(summary = "删除订阅", description = "置 deleted，投递日志保留")
    @RequirePermission("webhook.manage")
    @Auditable(module = "webhook", action = "删除Webhook订阅",
            objectIdExpr = "#id", objectDescExpr = "#result.name()")
    @DeleteMapping("/{id}")
    public WebhookAdminService.WebhookView delete(@PathVariable Long id) {
        return service.delete(id);
    }

    @Operation(summary = "轮换签名密钥", description = "旧密钥立即失效；返回的新密钥明文仅此一次展示")
    @RequirePermission("webhook.manage")
    @Auditable(module = "webhook", action = "轮换Webhook签名密钥",
            objectIdExpr = "#id", objectDescExpr = "#result.name()")
    @PostMapping("/{id}/rotate-secret")
    public WebhookAdminService.CreatedWebhook rotateSecret(@PathVariable Long id) {
        return service.rotateSecret(id);
    }

    @Operation(summary = "发送测试事件",
            description = "向该订阅投一条 webhook.test 事件，走真实签名/日志链路；失败不重试，结果看投递日志")
    @RequirePermission("webhook.manage")
    @Auditable(module = "webhook", action = "发送Webhook测试事件", objectIdExpr = "#id")
    @PostMapping("/{id}/test")
    public WebhookAdminService.TestResult sendTest(@PathVariable Long id) {
        return service.sendTest(id);
    }

    @Operation(summary = "投递日志", description = "按订阅分页倒序；可按 final_status 过滤（success/failed/abandoned）")
    @RequirePermission("webhook.view")
    @GetMapping("/{id}/logs")
    public WebhookAdminService.LogPage logs(@PathVariable Long id,
                                            @Parameter(description = "终态过滤") @RequestParam(required = false) String finalStatus,
                                            @Valid @ParameterObject PageQuery pageQuery) {
        return service.logs(id, finalStatus, pageQuery.page(), pageQuery.size());
    }

    // ============================== 请求 DTO ==============================

    public record UpsertRequest(
            @NotBlank(message = "名称必填") String name,
            @NotBlank(message = "回调地址必填") String targetUrl,
            @NotEmpty(message = "至少订阅一个事件") List<String> subscribedEvents) {
    }
}
