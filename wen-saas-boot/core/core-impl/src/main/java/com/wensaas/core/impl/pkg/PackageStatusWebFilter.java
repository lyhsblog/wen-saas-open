package com.wensaas.core.impl.pkg;

import com.wensaas.core.api.pkg.PackageStatusException;
import com.wensaas.core.api.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

/**
 * 套餐状态 Web 拦截器。
 *
 * <p>运行于 {@code TenantContextWebFilter}（+100）之后、{@code TenantActivityWebFilter}（+150）之前，
 * 对业务模块 API 路径做套餐权限拦截：
 * <ul>
 *   <li>未购买（无套餐覆盖）→ 读写皆拦截，HTTP 402 {@code PACKAGE_UNAUTHORIZED}</li>
 *   <li>套餐已到期只读 + 写方法 → HTTP 403 {@code PACKAGE_READONLY}</li>
 *   <li>免拦路径（/api/v1/me/、/api/v1/base/ 等底座能力）未注册到 {@link ModuleApiRouteRegistry}，自动跳过</li>
 *   <li>TenantContext 为 null（白名单/未登录）→ 跳过</li>
 * </ul>
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 130)
public class PackageStatusWebFilter extends OncePerRequestFilter {

    private static final Set<String> WRITE_METHODS =
            Set.of("POST", "PUT", "PATCH", "DELETE");

    private final PackageStatusGuard guard;
    private final ModuleApiRouteRegistry routeRegistry;

    public PackageStatusWebFilter(PackageStatusGuard guard, ModuleApiRouteRegistry routeRegistry) {
        this.guard = guard;
        this.routeRegistry = routeRegistry;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain chain) throws ServletException, IOException {
        Long tenantId = TenantContext.get();
        if (tenantId == null || tenantId == TenantContext.PLATFORM_TENANT_ID) {
            chain.doFilter(request, response);
            return;
        }
        String path = request.getRequestURI();
        Optional<String> moduleCode = routeRegistry.findModuleCode(path);
        if (moduleCode.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }
        boolean isWrite = WRITE_METHODS.contains(request.getMethod().toUpperCase());
        try {
            guard.requireAccess(tenantId, moduleCode.get(), isWrite);
        } catch (PackageStatusException e) {
            writeError(response, e);
            return;
        }
        chain.doFilter(request, response);
    }

    private static void writeError(HttpServletResponse response, PackageStatusException e) throws IOException {
        int status = e.getCode() == PackageStatusException.Code.PACKAGE_UNAUTHORIZED ? 402 : 403;
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        String code = e.getCode().name();
        String body = "{\"status\":" + status + ",\"title\":\"Package access denied\","
                + "\"detail\":\"" + e.getMessage() + "\","
                + "\"type\":\"https://wen-saas-boot/errors/package/" + code.toLowerCase() + "\","
                + "\"code\":\"" + code + "\","
                + "\"moduleCode\":\"" + e.getModuleCode() + "\"}";
        response.getWriter().write(body);
    }
}
