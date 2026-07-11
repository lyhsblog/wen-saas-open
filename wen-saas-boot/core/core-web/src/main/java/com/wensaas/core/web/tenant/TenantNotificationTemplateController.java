package com.wensaas.core.web.tenant;

import com.wensaas.core.api.audit.Auditable;
import com.wensaas.core.api.auth.permission.RequirePermission;
import com.wensaas.core.api.notification.template.TenantNotificationTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
 * 租户后台 · 系统管理 · 通知模板。
 *
 * <p>文案 / 触发场景 / 变量只读；主管理员仅可定制 启停 / 渠道 / 优先级。
 * 查看：{@code notification_template.view}；编辑：{@code notification_template.edit}（主管理员恒可）。
 */
@Tag(name = "tenant-notification-template", description = "租户后台 · 通知模板")
@SecurityRequirement(name = "bearer-jwt")
@RestController
@RequestMapping("/api/v1/base/notification-templates")
public class TenantNotificationTemplateController {

    private final TenantNotificationTemplateService service;

    public TenantNotificationTemplateController(TenantNotificationTemplateService service) {
        this.service = service;
    }

    @Operation(summary = "通知模板列表",
            description = "按业务模块 + 编码排序，叠加本租户偏好后的生效态；可按 moduleCode / 关键字过滤")
    @RequirePermission("notification_template.view")
    @GetMapping
    public List<TenantNotificationTemplateService.TemplateListItem> list(
            @Parameter(description = "业务模块编码过滤") @RequestParam(value = "moduleCode", required = false) String moduleCode,
            @Parameter(description = "编码/名称模糊关键字") @RequestParam(value = "keyword", required = false) String keyword) {
        return service.list(moduleCode, keyword);
    }

    @Operation(summary = "渠道元数据", description = "返回全部 Channel 及保底标记，供前端渲染渠道开关")
    @RequirePermission("notification_template.view")
    @GetMapping("/channels")
    public List<TenantNotificationTemplateService.ChannelMeta> channels() {
        return service.listChannels();
    }

    @Operation(summary = "通知模板详情", description = "含只读文案 / 变量 + 平台默认设定 + 本租户生效设定")
    @RequirePermission("notification_template.view")
    @GetMapping("/{code}")
    public TenantNotificationTemplateService.TemplateDetail get(@PathVariable String code) {
        return service.get(code);
    }

    @Operation(summary = "更新偏好（启停 / 渠道 / 优先级）",
            description = "目标态全量覆盖；与默认一致时自动清除偏好。sys.* 不可停用")
    @RequirePermission("notification_template.edit")
    @Auditable(module = "notification_template", action = "更新偏好",
            objectType = "", objectIdExpr = "#code")
    @PutMapping("/{code}")
    public TenantNotificationTemplateService.TemplateDetail updatePref(
            @PathVariable String code, @Valid @RequestBody UpdatePrefRequest req) {
        return service.updatePref(code, new TenantNotificationTemplateService.UpdatePrefCommand(
                req.enabled(), req.channels(), req.priority()));
    }

    @Operation(summary = "恢复默认（清除本租户定制）")
    @RequirePermission("notification_template.edit")
    @Auditable(module = "notification_template", action = "重置偏好",
            objectType = "", objectIdExpr = "#code")
    @PostMapping("/{code}/reset")
    public TenantNotificationTemplateService.TemplateDetail reset(@PathVariable String code) {
        return service.resetPref(code);
    }

    // ===================== 请求 DTO =====================

    /**
     * 偏好更新请求。三字段均可空：
     * {@code enabled} null 视作 true；{@code channels} null 表示继承默认；{@code priority} null 表示继承默认。
     */
    public record UpdatePrefRequest(
            Boolean enabled,
            List<String> channels,
            TenantNotificationTemplateService.Priority priority) {
    }
}
