package com.wensaas.core.api.auth.permission;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注 Controller 方法，声明该端点需要数据权限过滤。
 *
 * <p>{@link #value()} 指定 action code（即 {@code PermissionDef.code}，如 {@code "crm_demo.customer.view"}），
 * {@code DataPermissionAspect} 将其写入 {@code DataPermissionContext}，数据权限拦截器据此查询该 action
 * 在当前用户各角色下配置的数据范围并注入 WHERE 条件。
 *
 * <p>本注解自足：不依赖同方法上的 {@link RequirePermission}，两者独立共存。
 *
 * <p>{@link #tables()} 可选；省略时过滤当前 SQL 里命中注册表的所有表（适用于无跨注册表 JOIN
 * 的简单查询）。当查询含多个注册表 JOIN 时，应显式声明主表以防止关联表被误过滤。
 *
 * <p>典型用法：
 * <pre>{@code
 * // 简单查询：过滤所有注册表
 * @DataPermission("crm_demo.customer.view")
 * @RequirePermission("crm_demo.customer.view")
 * @GetMapping("/customers")
 * public Page<Customer> list(...) {}
 *
 * // JOIN 含其他注册表时：声明只过滤主表
 * @DataPermission(value = "crm_demo.customer.view", tables = "crm_demo_customer")
 * @RequirePermission("crm_demo.customer.view")
 * @GetMapping("/customers")
 * public Page<Customer> list(...) {}
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DataPermission {

    /** action code，即 {@code PermissionDef.code}（如 {@code "crm_demo.customer.view"}）。 */
    String value();

    /**
     * 限定只过滤哪几张注册表（物理表名，如 {@code "crm_demo_customer"}）。
     * 省略或空数组 = 过滤当前 SQL 中所有注册表。
     */
    String[] tables() default {};
}
