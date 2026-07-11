package com.wensaas.core.web.me;

import com.wensaas.core.api.auth.user.CapabilitiesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前用户视角（{@code /api/v1/me/**}）。
 *
 * <p>登录后立即由前端拉取 {@code /api/v1/me/capabilities} 写入 Zustand store，作为 PermissionGate /
 * PackageGate / SidebarMenu 的唯一事实源。
 */
@Tag(name = "me", description = "当前用户视角")
@SecurityRequirement(name = "bearer-jwt")
@RestController
@RequestMapping("/api/v1/me")
public class CapabilitiesController {

    private final CapabilitiesService capabilitiesService;

    public CapabilitiesController(CapabilitiesService capabilitiesService) {
        this.capabilitiesService = capabilitiesService;
    }

    @Operation(summary = "当前用户能力包",
            description = "聚合权限点 / 数据范围 / 可见部门 / 已上线模块 / 套餐 / 菜单树。"
                    + "权限与数据范围底层缓存 1h（PermissionResolver）")
    @GetMapping("/capabilities")
    public CapabilitiesService.Capabilities capabilities() {
        return capabilitiesService.resolve();
    }
}
