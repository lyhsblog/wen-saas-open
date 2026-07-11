package com.wensaas.core.web.customfield;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Map;

/**
 * 声明式收集自定义字段等值筛选参数。标注在 Controller 方法的 {@link Map Map&lt;String, Object&gt;}
 * 参数上，由 {@link CustomFieldEqualsArgumentResolver} 从请求 query 参数中摘出全部
 * {@code cf_} 前缀且非空白的项注入（无命中注入空 Map，参数永不为 null）。
 *
 * <pre>{@code
 * @GetMapping
 * public PageResult list(@RequestParam(required = false) String status,
 *                        @CustomFieldEquals Map<String, Object> cfEquals, ...) {
 *     // service 内直接 cfEquals.forEach(wrapper::eq)：cf_* 列由 CustomFieldQueryRewriter
 *     // 翻译为 extra->>'cf_x'，仅索引化字段允许（未索引化查询被底座拦截）
 * }
 * }</pre>
 *
 * <p>该参数不出现在 OpenAPI 文档（已注册 springdoc 忽略）；接口对 {@code cf_*} 筛选的支持
 * 在 {@code @Operation} 描述里说明即可。
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CustomFieldEquals {
}
