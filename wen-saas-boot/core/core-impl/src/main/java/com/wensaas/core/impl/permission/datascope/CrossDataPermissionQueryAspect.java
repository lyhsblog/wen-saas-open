package com.wensaas.core.impl.permission.datascope;

import com.wensaas.core.impl.tenant.isolation.IgnoreStrategyScopes;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;

/**
 * 处理 {@link com.wensaas.core.api.auth.permission.CrossDataPermissionQuery} 注解：
 * 方法（或类）执行期间打开 MP 的 {@code dataPermission=true} 忽略策略，
 * 让 {@code DataPermissionInterceptor} 在本线程内跳过 SQL 改写；方法结束后清理。
 *
 * <p>模式与 {@code CrossTenantQueryAspect} 完全对称——同样经 {@link IgnoreStrategyScopes}
 * 管理作用域栈，避免与 {@code @CrossTenantQuery} 共存或嵌套权限加载时被内层 clear 清掉旁路。
 */
@Aspect
@Order(0)
public class CrossDataPermissionQueryAspect {

    @Around("@annotation(com.wensaas.core.api.auth.permission.CrossDataPermissionQuery)"
            + " || @within(com.wensaas.core.api.auth.permission.CrossDataPermissionQuery)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        try (var ignored = IgnoreStrategyScopes.enter(IgnoreStrategyScopes.DATA_PERMISSION)) {
            return pjp.proceed();
        }
    }
}
