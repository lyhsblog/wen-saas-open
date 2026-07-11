package com.wensaas.core.impl.tenant.isolation;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 处理 {@link com.wensaas.core.api.tenant.CrossTenantQuery} 注解：
 * 方法（或类）执行期间打开 MyBatis-Plus 的 {@code tenantLine=true} 忽略策略，
 * 让 {@link com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor}
 * 在本线程内跳过 SQL 改写；方法结束后清理。
 *
 * <p>嵌套调用安全：经由 {@link IgnoreStrategyScopes} 管理每线程作用域栈，退出只回退本层、
 * 恢复外层并集。MP 原生 {@code InterceptorIgnoreHelper} 是覆盖式 ThreadLocal（无栈），
 * 直接 handle/clear 嵌套时内层 clear 会把外层旁路一并清空——典型踩点见
 * {@code IgnoreStrategyScopes} 类注释（2026-06-10 JourneyOne 偶发失败）。
 */
@Aspect
@Component
@Order(0)
public class CrossTenantQueryAspect {

    @Around("@annotation(com.wensaas.core.api.tenant.CrossTenantQuery)"
            + " || @within(com.wensaas.core.api.tenant.CrossTenantQuery)")
    public Object aroundCrossTenant(ProceedingJoinPoint pjp) throws Throwable {
        try (var ignored = IgnoreStrategyScopes.enter(IgnoreStrategyScopes.TENANT_LINE)) {
            return pjp.proceed();
        }
    }
}
