package com.wensaas.core.web.role;

import com.wensaas.core.api.audit.Auditable;
import com.wensaas.core.api.auth.permission.RequirePermission;
import com.wensaas.core.api.auth.permission.TenantRecommendedRoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
 * 租户侧推荐角色（读 + 启用）。
 */
@Tag(name = "tenant-recommended-role", description = "租户后台 · 推荐角色启用")
@SecurityRequirement(name = "bearer-jwt")
@RestController
@RequestMapping("/api/v1/base/recommended-roles")
public class TenantRecommendedRoleController {

    private final TenantRecommendedRoleService service;

    public TenantRecommendedRoleController(TenantRecommendedRoleService service) {
        this.service = service;
    }

    @Operation(summary = "列出可启用的推荐角色",
            description = "已发布且关联本租户已勾选套餐包的模板；activated=true 表示已启用")
    @RequirePermission("role.list")
    @GetMapping
    public List<TenantRecommendedRoleService.RecommendedRoleItem> listAvailable() {
        return service.listAvailable();
    }

    @Operation(summary = "启用预览",
            description = "返回可调名称 / 数据范围 / 权限点（valid=false 为当前未注册/未购模块权限点，标灰）")
    @RequirePermission("role.list")
    @GetMapping("/{templateId}/preview")
    public TenantRecommendedRoleService.ActivatePreview preview(@PathVariable long templateId) {
        return service.preview(templateId);
    }

    @Operation(summary = "启用推荐角色（生成快照自定义角色）")
    @RequirePermission("role.create")
    @Auditable(module = "__core__", action = "启用推荐角色", objectType = "",
            objectIdExpr = "#templateId")
    @PostMapping("/{templateId}/activate")
    public ActivateResult activate(@PathVariable long templateId,
                                   @RequestBody(required = false) ActivateRequest req) {
        ActivateRequest r = req == null ? new ActivateRequest(null, null, null) : req;
        long roleId = service.activate(templateId, new TenantRecommendedRoleService.ActivateCommand(
                r.name(), r.dataScope(), r.permissionCodes()));
        return new ActivateResult(roleId);
    }

    /** 启用入参（均可空，空则沿用模板默认）。 */
    public record ActivateRequest(String name, Integer dataScope, List<String> permissionCodes) {
    }

    /** 启用结果。roleId 为雪花 id，JSON 序列化为字符串。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    public static class ActivateResult {
        private long roleId;

    }
}
