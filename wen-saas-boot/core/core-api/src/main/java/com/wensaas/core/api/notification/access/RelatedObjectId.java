package com.wensaas.core.api.notification.access;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记 DTO 中承载「关联对象 ID」的字段（String，兼容非雪花 ID）。详见 {@link RelatedObjectGuarded}。
 * 探针以此值作为 {@code canAccess} 的入参；为 null 时默认放行。
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RelatedObjectId {
}
