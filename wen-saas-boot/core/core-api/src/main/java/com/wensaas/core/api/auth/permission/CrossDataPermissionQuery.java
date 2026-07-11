package com.wensaas.core.api.auth.permission;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 在被注解的方法（或类）执行期间绕过数据权限拦截器。
 *
 * <p>用于系统内部、运营审计、迁移脚本等需要看到全租户全部数据的场景。
 * 与 {@link com.wensaas.core.api.tenant.CrossTenantQuery} 正交：是否同时绕过多租户由调用方按需组合标注。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface CrossDataPermissionQuery {
}
