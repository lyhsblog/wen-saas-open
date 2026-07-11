package com.wensaas.core.api.namesource;

/**
 * {@link TransField} 回填某字段时的租户作用域取向。
 *
 * <p>取数发生在 service 构造响应后、由 {@code TransFiller} 触发；本枚举决定那一次 {@code resolve}
 * 期间是否旁路 MP 的 {@code TenantLineInnerInterceptor}（租户线）。作用域由 {@code TransFiller}
 * <b>就地</b>开/关（{@code IgnoreStrategyScopes}），不经 AOP、不挂在源上——故同一个普通源
 * （如 {@code sys.user}）既能给租户端按 {@link #FOLLOW} 翻本租户、也能给运营端按 {@link #CROSS}
 * 翻别租户，不必为跨租户单独再写一个源。
 *
 * <p><b>只提供 FOLLOW / CROSS 两态，不提供「强制当前租户」</b>：{@code IgnoreStrategyScopes} 是并集(OR)
 * 栈语义，内层无法「反向取消」外层已生效的旁路；而外层是否旁路租户线由调用链上任意位置（含业务模块自己
 * 开的 {@code IgnoreStrategyScopes} / 裸 {@code InterceptorIgnoreHelper}）决定，框架管不住。所以「强制回到
 * 当前租户」无法靠作用域开关可靠保证——真要某次取数严格限本租户，应在源的查询里写显式 {@code tenant_id}
 * 谓词（不依赖环境作用域），而非寄望一个 scope 枚举值。
 */
public enum TenantScope {

    /**
     * 跟随当前线程作用域（默认，安全取向）。外层无跨租户作用域时租户线照常注入 {@code tenant_id}
     * → 只翻当前租户；外层本就在 {@code @CrossTenantQuery} / {@code IgnoreStrategyScopes} 内时，
     * 这次解析自然也随之旁路（与外层一致）。
     */
    FOLLOW,

    /**
     * 本次名称解析旁路租户线，跨租户翻名（<b>仅运营端</b>）。{@code TransFiller} 在 {@code resolve}
     * 外包一层 {@code IgnoreStrategyScopes.enter(TENANT_LINE)}，作用域只圈这一次解析、不放开主查询。
     *
     * <p><b>安全约束</b>：{@code CROSS} 只允许出现在标了 {@link CrossTenantView}（运营专属）的 DTO 上；
     * 租户端 DTO 误标 {@code CROSS} 会静默翻出别租户数据（泄露），由 {@code TransSpecRegistry} 解析期
     * + {@code TransFieldScanner} 启动期 fail-fast 拦截。
     *
     * <p><b>不适用</b>「全局 + 租户私有」混合数据（如字典 {@code tenant_id IN (0, ?)}）：{@code CROSS}
     * 是二元全开会泄露别租户私有项，{@code FOLLOW} 又漏全局项——这类须在源里写显式谓词，不归本枚举。
     */
    CROSS
}
