package com.wensaas.core.api.spi;

import org.springframework.stereotype.Component;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 业务模块标注注解，语义等同 {@link Component}，便于 Spring 扫描识别。
 *
 * <p>用法：在实现 {@link BusinessModuleDescriptor} 的类上添加该注解。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface BusinessModule {

    /**
     * 可选的 Bean 名称，留空则由 Spring 推断。
     */
    String value() default "";
}
