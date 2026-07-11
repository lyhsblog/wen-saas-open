package com.wensaas.core.api.auth.permission;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.auth.user.UserPrincipal;

/**
 * 功能权限检查注解。
 *
 * <p>支持两种用法：
 * <ul>
 *   <li>{@code @RequirePermission("crm.customer.view")}：单一权限，等价于 {@code value()}</li>
 *   <li>{@code @RequirePermission(anyOf = {"a.b", "c.d"})}：任一权限命中即放行</li>
 * </ul>
 *
 * <p>语义：
 * <ul>
 *   <li>{@link UserContext} 缺失 → 401 NOT_AUTHENTICATED</li>
 *   <li>主管理员（{@link UserPrincipal#tenantOwner()} == true）→ 直接放行</li>
 *   <li>用户权限点集合不命中 → 403 FORBIDDEN</li>
 * </ul>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {

    /**
     * 单一权限点编码（与 {@link #anyOf()} 二选一）。
     *
     * <p>值必须与 {@code PermissionDef.code()} 严格对齐，即已注册进 {@code PermissionRegistry}
     * 的权限点编码（底座由 {@code BasePermissionContributor} 注册、业务模块经
     * {@code BusinessModuleDescriptor.permissions()} 注册）。
     * 未登记的 code 可通过拦截，但不会出现在角色权限配置页。
     */
    String value() default "";

    /** 任一权限点命中即放行（与 {@link #value()} 二选一；同时给定时取并集）。 */
    String[] anyOf() default {};
}
