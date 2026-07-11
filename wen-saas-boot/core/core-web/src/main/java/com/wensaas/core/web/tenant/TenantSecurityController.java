package com.wensaas.core.web.tenant;

import com.wensaas.core.api.audit.Auditable;
import com.wensaas.core.api.auth.permission.RequirePermission;
import com.wensaas.core.api.tenant.selfservice.TenantWatermarkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * 租户后台 · 系统管理 · 安全合规（防截屏水印开关）。
 *
 * <p>查看：任意已认证租户用户可读当前开关（设置页据此回显）；修改：{@code tenant.security.watermark.edit}
 * （未在权限目录预置 → 主管理员恒可、其余角色默认不可改，符合「水印为租户级安全策略」口径）。
 * 当前开关态同时经 {@code /api/v1/me/capabilities} 的 {@code screenshotWatermark} 下发用于渲染门控。
 */
@Tag(name = "tenant-security", description = "租户后台 · 安全合规（防截屏水印开关）")
@SecurityRequirement(name = "bearer-jwt")
@RestController
@RequestMapping("/api/v1/base/security")
public class TenantSecurityController {

    private final TenantWatermarkService watermarkService;

    public TenantSecurityController(TenantWatermarkService watermarkService) {
        this.watermarkService = watermarkService;
    }

    @Operation(summary = "当前租户防截屏水印开关")
    @GetMapping("/watermark")
    public WatermarkView getWatermark() {
        return new WatermarkView(watermarkService.isEnabled());
    }

    @Operation(summary = "设置防截屏水印开关（主管理员）")
    @RequirePermission("tenant.security.watermark.edit")
    @Auditable(module = "sys.security", action = "更新水印", objectType = "")
    @PutMapping("/watermark")
    public WatermarkView updateWatermark(@Valid @RequestBody WatermarkUpdateRequest req) {
        watermarkService.setEnabled(req.enabled());
        return new WatermarkView(watermarkService.isEnabled());
    }

    /** @param enabled true=开启防截屏水印（默认） */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    public static class WatermarkView {
        private boolean enabled;
    }

    public record WatermarkUpdateRequest(@NotNull Boolean enabled) {}
}
