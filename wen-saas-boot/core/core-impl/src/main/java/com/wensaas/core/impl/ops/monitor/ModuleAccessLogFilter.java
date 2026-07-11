package com.wensaas.core.impl.ops.monitor;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.auth.user.UserPrincipal;
import com.wensaas.core.impl.pkg.ModuleApiRouteRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 业务模块访问埋点 Filter（O9 平台监控看板的「各模块调用 / 月活」数据源）。
 *
 * <p>顺序 {@code HIGHEST_PRECEDENCE + 140}：晚于 {@code AuthenticationFilter}（+50，注入 UserContext）
 * 与 {@code TenantContextWebFilter}（+100），因此 chain.doFilter 全程及本 Filter 的 finally 内
 * {@link UserContext} 仍有效（本 Filter 是 AuthenticationFilter 的内层，对方 finally 尚未执行）。
 *
 * <p>仅记录命中某业务模块 API 前缀的请求（{@link ModuleApiRouteRegistry#findModuleCode}）。平台保留路径
 * （/api/auth、/api/v1/ops、/api/v1/me 等未注册前缀）与 openapi（/api/openapi 独立前缀）均不命中、不记。
 * 记录经 {@link ModuleAccessBatchBuffer} 异步批量入库，对请求链路零阻塞；异常 fail-soft 不影响业务。
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 140)
public class ModuleAccessLogFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ModuleAccessLogFilter.class);

    private final ModuleApiRouteRegistry routeRegistry;
    private final ModuleAccessBatchBuffer buffer;

    public ModuleAccessLogFilter(ModuleApiRouteRegistry routeRegistry, ModuleAccessBatchBuffer buffer) {
        this.routeRegistry = routeRegistry;
        this.buffer = buffer;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Optional<String> moduleCode = routeRegistry.findModuleCode(request.getRequestURI());
        if (moduleCode.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }
        long startNanos = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            try {
                record(request, response, moduleCode.get(), startNanos);
            } catch (RuntimeException e) {
                log.warn("module-access.record.fail module={} reason={}", moduleCode.get(), e.getMessage());
            }
        }
    }

    private void record(HttpServletRequest request, HttpServletResponse response,
                        String moduleCode, long startNanos) {
        UserPrincipal principal = UserContext.get();
        SysModuleAccessLog row = new SysModuleAccessLog();
        row.setId(IdWorker.getId());
        row.setTenantId(principal != null ? principal.tenantId() : 0L);
        row.setModuleCode(moduleCode);
        // userId=0 表示无操作人（匿名 / openapi 凭证），不计入模块 DAU 去重
        row.setOperatorId(principal != null && principal.userId() != 0L ? principal.userId() : null);
        row.setMethod(request.getMethod());
        row.setPath(request.getRequestURI());
        row.setStatusCode(response.getStatus());
        row.setDurationMs((int) ((System.nanoTime() - startNanos) / 1_000_000L));
        row.setCreatedAt(LocalDateTime.now());
        buffer.offer(row);
    }
}
