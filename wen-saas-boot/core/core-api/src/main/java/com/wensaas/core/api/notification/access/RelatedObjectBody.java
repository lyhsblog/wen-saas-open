package com.wensaas.core.api.notification.access;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记 DTO 中的「正文」字段（String）。详见 {@link RelatedObjectGuarded}。
 * 无权访问时该字段被替换为 {@link RelatedObjectGuarded#maskedBodyText()}。
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RelatedObjectBody {
}
