package com.wensaas.core.api.namesource;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明该字段是 {@code TransFiller.fill} 的<b>下钻入口</b>——其持有的嵌套对象（或对象的集合 / Map / 数组）
 * 内还有 {@link TransField} 待回填，需递归进去处理。
 *
 * <p><b>下钻是声明式、非自动</b>：{@code fill(root)} 只穿透根对象本身（及作为根直接传入的集合 / Map / 数组），
 * 对宿主对象的<b>普通对象属性字段一律不下钻</b>，除非该字段标了本注解。如此：
 * <ul>
 *   <li>避免遍历「本就由别的服务取出、已填充过」的属性对象，多走一遍；</li>
 *   <li>避免遍历开发者并不需要回填的嵌套结构，省解析 + 遍历开销；</li>
 *   <li>下钻范围由开发者按需精确声明，而非靠包名启发式自动铺开。</li>
 * </ul>
 *
 * <p>标注位置须是<b>可下钻字段</b>：自有对象、{@code Collection} / {@code Map} / 数组（其元素 / 值为自有
 * 对象）。标在标量 / JDK 值类型（{@code String} / {@code Long} 等）上 → {@code TransSpecRegistry} 解析期
 * fail-fast。{@link TransField} 的 target / ref 字段无需、也不应再标本注解。
 *
 * <pre>{@code
 * class MemberDetail {
 *     @TransDescend DeptBinding mainDept;            // 单个嵌套对象
 *     @TransDescend List<DeptBinding> secondaryDepts; // 对象集合
 *     List<RoleBrief> roles;                          // 无 @TransField → 不标，不下钻
 * }
 * }</pre>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TransDescend {
}
