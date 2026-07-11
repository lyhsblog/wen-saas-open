package com.wensaas.core.api.namesource;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 类级标记：声明该响应 DTO 是<b>运营专属</b>视图，允许其字段使用 {@link TransField} 的
 * {@link TenantScope#CROSS}（跨租户翻名）。
 *
 * <p><b>为什么需要显式 opt-in</b>：声明式 {@code @TransField(scope = CROSS)} 一旦误标到<b>租户端</b>
 * 可达的 DTO 上，{@code TransFiller} 会静默翻出别租户数据（非空泄露，「翻不出留 null」兜不住）。
 * 静态判定「一个 DTO 是不是租户端可达」很难，故反转为显式声明：{@code CROSS} <b>只允许</b>出现在标了
 * 本注解的类上——把「证明可达性」（难）换成「显式声明运营专属」（易、默认安全、review 可见）。
 *
 * <p>一致性由两道关卡保证：{@code TransSpecRegistry} 解析某 DTO 时若发现 {@code CROSS} 字段却无本标记，
 * 立即抛异常（fill 前置，泄露前即拦）；{@code TransFieldScanner} 启动期扫描所有本标记类、force 解析 +
 * 校验引用 type 已注册，把问题提前到启动期。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CrossTenantView {

    /** 可选说明（用途、调用端限制等）。 */
    String value() default "";
}
