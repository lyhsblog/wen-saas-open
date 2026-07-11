package com.wensaas.core.impl.permission.eval;

import com.wensaas.core.api.auth.permission.PermissionDeniedException;
import com.wensaas.core.api.auth.permission.RequirePermission;
import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.auth.user.UserPrincipal;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.Order;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * {@link RequirePermission} 注解的 AOP 切面。
 *
 * <p>流程：UserContext 缺失 → 401；tenantOwner=true → 跳过；否则查 {@link PermissionResolver}
 * → 比对 value/anyOf → 不命中抛 403。
 *
 * <p>{@code @Order(10)}：晚于 {@code CrossTenantQueryAspect}(@Order(0))，确保 TenantContext 已被
 * 接管；早于业务方自定义切面（业务方一般不指定 Order 或使用更大值）。
 */
@Aspect
@Order(10)
public class RequirePermissionAspect {

    private final PermissionResolver resolver;

    public RequirePermissionAspect(PermissionResolver resolver) {
        this.resolver = resolver;
    }

    @Around("@annotation(com.wensaas.core.api.auth.permission.RequirePermission)"
            + " || @within(com.wensaas.core.api.auth.permission.RequirePermission)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        RequirePermission ann = resolveAnnotation(pjp);
        if (ann == null) {
            return pjp.proceed();
        }

        UserPrincipal principal = UserContext.get();
        if (principal == null) {
            throw new PermissionDeniedException(
                    PermissionDeniedException.Code.NOT_AUTHENTICATED,
                    "权限检查失败：未登录或会话已失效");
        }
        if (principal.tenantOwner()) {
            return pjp.proceed();
        }

        if (granted(ann, principal)) {
            return pjp.proceed();
        }
        throw new PermissionDeniedException(
                PermissionDeniedException.Code.FORBIDDEN,
                "权限不足：" + describe(ann));
    }

    /** 方法注解优先于类注解（@within）。 */
    private RequirePermission resolveAnnotation(ProceedingJoinPoint pjp) {
        MethodSignature sig = (MethodSignature) pjp.getSignature();
        Method method = sig.getMethod();
        RequirePermission ann = AnnotatedElementUtils.findMergedAnnotation(method, RequirePermission.class);
        if (ann != null) {
            return ann;
        }
        Class<?> target = pjp.getTarget().getClass();
        return AnnotatedElementUtils.findMergedAnnotation(target, RequirePermission.class);
    }

    /** value 命中或 anyOf 命中即放行；value/anyOf 都未声明（空注解）则已登录非 owner 即放行。 */
    boolean granted(RequirePermission ann, UserPrincipal principal) {
        boolean hasValue = !ann.value().isEmpty();
        boolean hasAnyOf = ann.anyOf().length > 0;
        if (hasValue && resolver.has(principal.tenantId(), principal.userId(), ann.value())) {
            return true;
        }
        if (hasAnyOf && resolver.hasAny(principal.tenantId(), principal.userId(), Arrays.asList(ann.anyOf()))) {
            return true;
        }
        return !hasValue && !hasAnyOf;
    }

    static String describe(RequirePermission ann) {
        if (!ann.value().isEmpty()) {
            return ann.value();
        }
        if (ann.anyOf().length > 0) {
            return "anyOf=" + String.join(",", ann.anyOf());
        }
        return "(empty)";
    }
}
