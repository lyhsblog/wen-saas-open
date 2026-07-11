package com.wensaas.core.api.tenant;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 跨租户查询声明。标注后该方法（或该类全部方法）执行期间，
 * 多租户拦截器跳过 {@code tenant_id} 注入。
 *
 * <p>仅底座 / 运营级代码可使用，业务模块禁止引用。
 *
 * <p>实现细节：由 {@code CrossTenantQueryAspect} 切面在方法进入时打开 MP 的
 * {@code InterceptorIgnoreHelper} tenantLine 标记，方法退出时清理。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CrossTenantQuery {

    /** 可选说明（用途、调用方限制等）。 */
    String value() default "";
}
