package com.wensaas.core.web.ops;

import com.wensaas.core.api.audit.Auditable;
import com.wensaas.core.api.auth.permission.RequirePermission;
import com.wensaas.core.api.notification.template.NotificationTemplateAdminService;
import com.wensaas.core.web.ops.dto.NotificationTemplateCreateRequest;
import com.wensaas.core.web.ops.dto.NotificationTemplateUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
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
 * 运营后台 · 通知模板管理。
 *
 * <p>管理对象为平台默认模板（{@code sys_notification_template.tenant_id=0}）。
 * sys.* 底座预置模板只读：不可改 / 停用 / 删除（返回 422 SYS_TEMPLATE_PROTECTED）。
 */
@Tag(name = "ops-notification-template", description = "运营后台 · 通知模板管理")
@SecurityRequirement(name = "bearer-jwt")
@RestController
@RequestMapping("/api/v1/ops/notification-templates")
public class NotificationTemplateOpsController {

    private final NotificationTemplateAdminService service;

    public NotificationTemplateOpsController(NotificationTemplateAdminService service) {
        this.service = service;
    }

    @Operation(summary = "通知模板列表",
            description = "默认返回全部状态；status=active|inactive 单态过滤；可按 moduleCode / 关键字过滤；按模块 + 编码排序")
    @RequirePermission("ops.notification_template.list")
    @GetMapping
    public List<NotificationTemplateAdminService.TemplateListItem> list(
            @Parameter(description = "状态过滤；默认 all（全部）")
            @RequestParam(value = "status", defaultValue = "ALL") NotificationTemplateAdminService.StatusFilter status,
            @Parameter(description = "业务模块编码过滤") @RequestParam(value = "moduleCode", required = false) String moduleCode,
            @Parameter(description = "编码/名称模糊关键字") @RequestParam(value = "keyword", required = false) String keyword) {
        return service.listTemplates(status, moduleCode, keyword);
    }

    @Operation(summary = "渠道元数据", description = "返回全部 Channel 及保底标记，供前端渲染渠道开关 / 文案 Tab")
    @RequirePermission("ops.notification_template.list")
    @GetMapping("/channels")
    public List<NotificationTemplateAdminService.ChannelMeta> channels() {
        return service.listChannels();
    }

    @Operation(summary = "通知模板详情", description = "含可用变量 / 默认渠道 / 各渠道文案")
    @RequirePermission("ops.notification_template.list")
    @GetMapping("/{code}")
    public NotificationTemplateAdminService.TemplateDetail get(@PathVariable String code) {
        return service.getTemplate(code);
    }

    @Operation(summary = "新建通知模板", description = "恒定 tenant_id=0 平台默认模板；服务端确保 STATION 保底；启用渠道文案不能为空")
    @RequirePermission("ops.notification_template.create")
    @Auditable(module = "notification_template", action = "新建通知模板", objectType = "",
            objectIdExpr = "#result.templateCode()", objectDescExpr = "#result.name()")
    @PostMapping
    public NotificationTemplateAdminService.TemplateDetail create(
            @Valid @RequestBody NotificationTemplateCreateRequest req) {
        return service.createTemplate(new NotificationTemplateAdminService.CreateTemplateCommand(
                req.templateCode(), req.moduleCode(), req.name(), req.description(),
                req.availableVars(), req.defaultChannels(), req.contents(),
                req.notificationCategory(), req.priority(), req.status()));
    }

    @Operation(summary = "编辑通知模板",
            description = "字段 null 表示不改；defaultChannels/contents/availableVars 非 null 时全量替换。sys.* 不可改")
    @RequirePermission("ops.notification_template.update")
    @Auditable(module = "notification_template", action = "编辑通知模板", objectType = "",
            objectIdExpr = "#code")
    @PutMapping("/{code}")
    public NotificationTemplateAdminService.TemplateDetail update(
            @PathVariable String code, @Valid @RequestBody NotificationTemplateUpdateRequest req) {
        return service.updateTemplate(code, new NotificationTemplateAdminService.UpdateTemplateCommand(
                req.name(), req.description(), req.availableVars(), req.defaultChannels(),
                req.contents(), req.notificationCategory(), req.priority(), req.status()));
    }

    @Operation(summary = "启用（status → active）")
    @RequirePermission("ops.notification_template.enable_disable")
    @Auditable(module = "notification_template", action = "启用通知模板", objectType = "",
            objectIdExpr = "#code")
    @PutMapping("/{code}/enable")
    public ResponseEntity<Void> enable(@PathVariable String code) {
        service.enable(code);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "停用（status → inactive）。停用后不再发送。sys.* 不可停用")
    @RequirePermission("ops.notification_template.enable_disable")
    @Auditable(module = "notification_template", action = "停用通知模板", objectType = "",
            objectIdExpr = "#code")
    @PutMapping("/{code}/disable")
    public ResponseEntity<Void> disable(@PathVariable String code) {
        service.disable(code);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "删除（软删）。sys.* 不可删除")
    @RequirePermission("ops.notification_template.delete")
    @Auditable(module = "notification_template", action = "删除通知模板", objectType = "",
            objectIdExpr = "#code")
    @DeleteMapping("/{code}")
    public ResponseEntity<Void> delete(@PathVariable String code) {
        service.deleteTemplate(code);
        return ResponseEntity.noContent().build();
    }
}
