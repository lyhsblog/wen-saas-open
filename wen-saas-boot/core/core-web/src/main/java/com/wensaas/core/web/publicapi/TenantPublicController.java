package com.wensaas.core.web.publicapi;

import com.wensaas.core.api.auth.permission.PublicEndpoint;
import com.wensaas.core.impl.tenant.TenantPublicQueryService;
import com.wensaas.core.web.publicapi.dto.TenantPublicView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公开租户信息查询。
 *
 * <p>支撑租户子域登录页 {@code {tenantCode}.基础域/login} 的解析逻辑（拿租户名/logo/状态渲染登录页）。
 * 仅暴露 5 个公开字段，不返回 mainAdminUserId / plans / 内部统计。
 *
 * <p>无鉴权（{@link PublicEndpoint}）；跨租户查询 / Logo 签发等编排下沉到
 * {@link TenantPublicQueryService}（架构审查 M11），本控制器仅做 HTTP 映射。
 */
@Tag(name = "public-tenant", description = "公开租户信息")
@SecurityRequirements
@PublicEndpoint
@RestController
@RequestMapping("/api/v1/public/tenants")
public class TenantPublicController {

    private final TenantPublicQueryService tenantPublicQueryService;

    public TenantPublicController(TenantPublicQueryService tenantPublicQueryService) {
        this.tenantPublicQueryService = tenantPublicQueryService;
    }

    @Operation(summary = "按 code 查询租户公开信息",
            description = "租户子域登录页渲染用；返回公开字段（含规范 code + 实时签发 Logo URL）；"
                    + "当 code 为旧子域名别名（90 天内）时按当前租户解析并回 canonicalCode 供前端跳规范子域；"
                    + "不存在或软删返 404 ProblemDetail")
    @GetMapping("/by-code/{code}")
    public TenantPublicView byCode(@PathVariable String code) {
        TenantPublicQueryService.PublicTenant t = tenantPublicQueryService.byCode(code);
        return new TenantPublicView(t.id(), t.code(), t.code(), t.name(), t.logoUrl(), t.status());
    }
}
