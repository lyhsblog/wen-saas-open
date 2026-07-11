package com.wensaas.core.api.notification.access;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注一个 DTO 在序列化时需做关联对象权限探针。
 *
 * <p>配合字段级 {@link RelatedObjectType} / {@link RelatedObjectId} / {@link RelatedObjectBody}
 * / {@link RelatedObjectLink} 使用：序列化时读取 type+id 调 {@code RelatedObjectAccessEvaluator}，
 * 无权访问则把 {@code @RelatedObjectBody} 字段替换为 {@link #maskedBodyText()}、{@code @RelatedObjectLink}
 * 字段置空，其余字段（如 id / name 摘要）保持原样。
 *
 * <p>被标注的 DTO 需有 public 无参构造（序列化器生成降级副本时使用，避免污染共享引用）。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RelatedObjectGuarded {

    /** 无权访问时正文降级文案。 */
    String maskedBodyText() default "该消息关联的内容已不可见";
}
