package com.wensaas.core.api.customfield;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 类级注解，标记此业务 entity 接入自定义字段框架 v2。
 *
 * <p>挂注解后由底座 {@code CustomFieldWriteInterceptor} 在 INSERT/UPDATE 时自动校验 {@code extra}
 * jsonb 容器内的值；{@code CustomFieldQueryRewriter} 在 SELECT 时把 WHERE 中 {@code cf_} 前缀的列引用
 * 翻译为 {@code extra->>'cf_xxx'}（仅已索引化字段允许，非索引化字段拒绝）。
 *
 * <p>业务 entity 协议（参见示例）：
 * <pre>{@code
 * @TableName(value = "crm_customer", autoResultMap = true)
 * @CustomFieldExtension(objectCode = "crm.customer")
 * public class Customer {
 *     @TableId Long id;
 *     Long tenantId;
 *     String name;
 *
 *     @TableField(typeHandler = JacksonTypeHandler.class)
 *     Map<String, Object> extra;     // ← jsonb 容器
 * }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface CustomFieldExtension {

    /** 业务对象 code，必须与 {@code CustomFieldObjectDef.code()} 一致。 */
    String objectCode();

    /** entity 上承载自定义字段值的 {@code Map<String,Object>} 字段名，默认 {@code extra}。 */
    String extraField() default "extra";
}
