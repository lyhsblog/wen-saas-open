package com.wensaas.core.impl.permission.datascope;

import com.wensaas.core.api.auth.permission.DataPermission;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import com.wensaas.core.impl.permission.eval.RequirePermissionAspect;

/**
 * {@link DataPermission} 注解的 AOP 切面。
 *
 * <p>将 {@link DataPermission#value()}（action code）和 {@link DataPermission#tables()}
 * 写入 {@link DataPermissionContext}（ThreadLocal），供 {@code DataPermissionHandlerImpl}
 * 在 MyBatis SQL 改写期间读取。{@code finally} 块保证清理，不泄漏至后续请求。
 *
 * <p>{@code @Order(11)}：晚于 {@code RequirePermissionAspect}（{@code @Order(10)}），
 * 确保功能权限检查（可能抛 403）先于数据权限上下文建立；早于业务方自定义切面。
 */
@Aspect
@Order(11)
public class DataPermissionAspect {

    @Around("@annotation(ann)")
    public Object around(ProceedingJoinPoint pjp, DataPermission ann) throws Throwable {
        DataPermissionContext.set(ann.value(), ann.tables());
        try {
            return pjp.proceed();
        } finally {
            DataPermissionContext.clear();
        }
    }
}
