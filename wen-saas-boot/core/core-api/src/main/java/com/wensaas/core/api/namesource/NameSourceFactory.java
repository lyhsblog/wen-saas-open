package com.wensaas.core.api.namesource;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;

/**
 * 名称源工厂：业务模块凭实体类 + 方法引用一行注册 {@link NameLookup}，无需下沉 {@code core.impl.*}。
 * 注入本工厂、{@code @Bean} 返回 {@link #source} 链式构建结果即可。
 *
 * <pre>{@code
 * // 名称主显（多数实体）
 * @Bean NameLookup crmCustomerNameSource(NameSourceFactory factory) {
 *     return factory.source("crm_demo.customer", "客户", true, CrmDemoCustomer.class, CrmDemoCustomer::getId)
 *             .primary  (CrmDemoCustomer::getName, "名称", MatchMode.PREFIX)
 *             .secondary(CrmDemoCustomer::getCode, "编码", MatchMode.EXACT)
 *             .build();
 * }
 *
 * // 编号主显（单据类，无名称或编号即主体）
 * @Bean NameLookup invoiceNameSource(NameSourceFactory factory) {
 *     return factory.source("doc.invoice", "单据", true, DocInvoice.class, DocInvoice::getId)
 *             .primary  (DocInvoice::getCode, "单据编号", MatchMode.EXACT)
 *             .secondary(DocInvoice::getName, "客户摘要", MatchMode.PREFIX)
 *             .render(RenderMode.BOTH)
 *             .build();
 * }
 * }</pre>
 *
 * <p>返回的实例同时实现 {@link NameLookup}（正向 id→名）与 {@code NameSearch}（反向名→id 下拉搜索），
 * 并内置租户线安全的查询改写（OR 组括号包裹防跨租泄露）——业务模块不必手写、也就不会踩相关坑。
 * 需 join / 特殊脱敏 / 跨库的实体仍可直接实现 {@link NameLookup} 接口。
 *
 * <p>方法引用类型用 MyBatis-Plus {@link SFunction}：既能 {@code apply} 取值，又能被 MP 反解列名进 WHERE
 * （正向翻译与反向搜索共用一套注册）。core-api 仅为此 {@code provided} 引入 {@code mybatis-plus-core}，
 * 运行时由 core-impl 的 MP 提供。
 */
public interface NameSourceFactory {

    /**
     * 开始构建一个默认名称源。链式补 {@link Builder#primary}（必填）、{@link Builder#secondary}（可选）、
     * {@link Builder#render}（默认 {@link RenderMode#PRIMARY}），最后 {@link Builder#build}。
     *
     * @param entityType       实体类型 code（全局唯一，与自定义字段 {@code objectCode} 同命名空间）
     * @param displayLabel     类型中文显示名（如「客户」）
     * @param tenantSearchable 是否允许在租户上下文反向搜索；platform-only / 跨租户实体须传 {@code false}
     * @param entityClass      实体类
     * @param idGetter         id getter 方法引用
     * @param <T>              实体类型
     */
    <T> Builder<T> source(String entityType, String displayLabel, boolean tenantSearchable,
                          Class<T> entityClass, SFunction<T, ?> idGetter);

    /**
     * 名称源构建器：以「有序字段列表」声明可搜 / 可显字段，第一个（{@link #primary}）即主显，{@code secondary}
     * 最多一个。每个字段带 {@code (getter, label, match)}——{@code label} 是该字段中文名（驱动前端提示文案 /
     * 列头），{@code match} 是其反向匹配模式。
     */
    interface Builder<T> {

        /** 主字段（必填）：主显 + 默认排序键。 */
        Builder<T> primary(SFunction<T, String> getter, String label, MatchMode match);

        /** 次字段（可选，最多一个）：下拉里的淡色辅助信息，也参与关键字搜索。 */
        Builder<T> secondary(SFunction<T, String> getter, String label, MatchMode match);

        /** 展示格式，默认 {@link RenderMode#PRIMARY}（仅主字段）。 */
        Builder<T> render(RenderMode mode);

        /**
         * 是否可作关联引用目标（默认 {@code true}）。权限 / 配置构件（如角色）传 {@code false}——能搜不可引用。
         * 见 {@link NameSearch#referenceable()}。
         */
        Builder<T> referenceable(boolean referenceable);

        /** 构建名称源（同时实现 {@link NameLookup} + {@code NameSearch}）。未调 {@link #primary} 抛错。 */
        NameLookup build();
    }
}
