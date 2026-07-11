package com.wensaas.core.api.namesource;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 实体字段「引用哪个实体类型」的声明。
 *
 * <p>标在外键字段上，声明该字段存的 id 指向哪个 {@link NameLookup#entityType()}。底座扫描所有
 * {@code @TableName} 实体收集这些声明，建立 {@code (表,列) → entityType} 注册表
 * （{@code com.wensaas.core.impl.namesource.ColumnRefRegistry}），供数据变更日志等消费方
 * 自动把 id 译成名称——业务只需标注，无需在审计配置表里重复登记「列→entityType」。
 *
 * <p>示例（业务实体内）：
 * <pre>{@code
 * @TableName("crm_customer")
 * public class CrmCustomer {
 *     @DisplayRef(value = EntityTypes.SYS_USER, label = "负责人")
 *     private Long ownerUserId;   // 列 owner_user_id 存用户 id；审计自动显示 负责人=张三(id)
 * }
 * }</pre>
 *
 * <p>列名解析：优先 {@code @TableField(value)}，否则按 MP 默认驼峰转下划线。
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DisplayRef {

    /** 被引用的实体类型 code（与某个 {@link NameLookup#entityType()} 对应，如 {@link EntityTypes#SYS_USER}）。 */
    String value();

    /** 可选：消费方（如审计）缺省展示用的中文列名；留空则由消费方各自的配置 / 列名兜底。 */
    String label() default "";
}
