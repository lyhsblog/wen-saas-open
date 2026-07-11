package com.wensaas.core.api.notification.access;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记 DTO 中承载「关联对象类型」的字段（String）。详见 {@link RelatedObjectGuarded}。
 * 序列化探针以此值路由到对应 {@code RelatedObjectAccessChecker}。
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RelatedObjectType {
}
