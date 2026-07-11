package com.wensaas.core.api.audit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记 DTO / Entity 字段为敏感字段。{@link Sensitives#maskByAnnotation(Object)} 投递日志前
 * 自动按 {@link SensitiveType} 脱敏。
 *
 * <p>v1 仅作用于直接字段，不递归嵌套对象 / List / Map。
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Sensitive {

    SensitiveType value() default SensitiveType.REMOVE;

    /** 仅当 {@link #value()} = {@link SensitiveType#CUSTOM} 时生效：对应 {@link SensitiveHandler#name()}。 */
    String handler() default "";
}
