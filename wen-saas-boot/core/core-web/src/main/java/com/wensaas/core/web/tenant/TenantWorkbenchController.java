package com.wensaas.core.web.tenant;

import com.wensaas.core.api.tenant.selfservice.TenantWorkbenchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 租户后台 · 工作台首页。
 *
 * <p>前缀 {@code /api/v1/base/workbench}，面向租户内任意已登录成员，主体取自 {@code UserContext}，
 * 无需额外权限点（自助首页，同 {@code TenantProfileController}）。套餐到期块的可见性由 service 按
 * {@code tenantOwner} 收敛——非主管理员该字段为 null。
 */
@Tag(name = "tenant-workbench", description = "租户后台 · 工作台首页")
@SecurityRequirement(name = "bearer-jwt")
@RestController
@RequestMapping("/api/v1/base/workbench")
public class TenantWorkbenchController {

    private final TenantWorkbenchService service;

    public TenantWorkbenchController(TenantWorkbenchService service) {
        this.service = service;
    }

    @Operation(summary = "顶部信息条 + 套餐到期 + 待办/消息/公告 固定三卡")
    @GetMapping("/overview")
    public TenantWorkbenchService.OverviewView overview() {
        return service.overview();
    }

    @Operation(summary = "业务数据卡片区（各业务模块贡献，按可见角色过滤 + 排序 + 实时求值）")
    @GetMapping("/cards")
    public List<TenantWorkbenchService.BusinessCardView> cards() {
        return service.businessCards();
    }
}
