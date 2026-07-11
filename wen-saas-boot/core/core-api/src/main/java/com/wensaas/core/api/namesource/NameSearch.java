package com.wensaas.core.api.namesource;

import java.util.List;

/**
 * 实体「关键字 → 候选实例」反向搜索 SPI（{@link NameLookup} 的反向）。
 *
 * <p>{@link NameLookup} 解决「id → 展示名」（正向、批量翻译外键列 / 审计渲染）；本接口解决反向：
 * 给定用户输入的<b>单个关键字</b>，OR 命中实体声明的全部 {@link #searchFields() 可搜字段}（各按自己的
 * {@link MatchMode}：名称类前缀、编号 / code 类精确），匹配出前 N 条候选实例（{@link NameHit}），供
 * 「选对象」类下拉 / 弹窗使用。已知消费者：审计日志按对象筛选、自定义字段 ref 选择器（{@code EntityRefPicker}）、
 * 业务页面任意「选人 / 选部门 / 选关联实例」类筛选或表单字段——通用反查能力，不限于审计或自定义字段场景。
 * 是<b>可选</b>能力——只有需要被反向搜索的实体才实现；未实现的实体类型不会出现在「可搜索类型」列表里。
 *
 * <p>底座对 id/code/name 列规整的实体提供默认实现
 * {@code com.wensaas.core.impl.namesource.LambdaNameSource}（同时实现 {@link NameLookup} 与本接口）；
 * 需 join / 加密列等值 / 跨库的实体自行实现本接口。
 *
 * <p>契约：
 * <ul>
 *   <li>{@link #entityType()} 与同实体的 {@link NameLookup#entityType()} 一致（同命名空间）；</li>
 *   <li>{@link #tenantSearchable()} 为 {@code false} 的类型（platform-only / 跨租户实体，如 {@code sys.tenant}）
 *   <b>禁止</b>在租户上下文内被搜索，聚合层须据此过滤 + 拒绝，防越权跨租户泄露；</li>
 *   <li>{@link #search} 按 {@link #searchFields()} 各字段的匹配模式 OR 命中，只返回 {@code limit} 内若干条、
 *   <b>不</b>统计总数、<b>不</b>分页；租户隔离由实现内部 / 租户线拦截器负责。</li>
 * </ul>
 */
public interface NameSearch {

    /** 处理的实体类型 code，与同实体 {@link NameLookup#entityType()} 一致。 */
    String entityType();

    /** 是否允许在租户上下文内搜索；platform-only / 跨租户实体须为 {@code false}（防越权）。 */
    boolean tenantSearchable();

    /**
     * 是否为「可被别处引用的数据记录实体」，即能否作为关联引用目标（自定义字段 REF 字段、{@code @DisplayRef}
     * 等）的候选。与 {@link #tenantSearchable()}（能否被反向搜索）<b>正交</b>：
     * <ul>
     *   <li>数据记录实体（成员 / 部门 / 业务对象）= {@code true}（默认）；</li>
     *   <li><b>权限 / 配置构件</b>（如角色）= {@code false}——能被搜索（审计「对象」筛选要用），但不是可被
     *   业务记录关联的数据对象。</li>
     * </ul>
     * 自定义字段 REF 目标 = {@code tenantSearchable && referenceable}（租户端既要能搜、又须是数据实体）。
     */
    default boolean referenceable() {
        return true;
    }

    /**
     * 可搜字段元数据（有序，第一个为主显字段）。驱动前端：提示文案「输入 主label/次label 搜索」、
     * 弹窗列展示。无次字段则只一项。
     */
    List<FieldMeta> searchFields();

    /**
     * 搜索候选：单关键字 OR 命中 {@link #searchFields()} 全部字段（各按其 {@link MatchMode}），返回不超过
     * {@code query.limit()} 条。不统计总数；{@code query.offset()} 供下拉 / 弹窗滚动加载续取下一页。
     */
    List<NameHit> search(SearchQuery query);

    /** 一个可搜字段的元数据。{@code label} 中文名（前端提示文案 / 列头）、{@code match} 匹配模式。 */
    record FieldMeta(String label, MatchMode match) {}

    /**
     * 搜索条件：单关键字 OR 命中全部可搜字段；实现可在 {@code keyword} 为纯数字时附带按 id 精确命中。
     * {@code offset} 用于滚动加载（不返回总数）。
     */
    record SearchQuery(String keyword, int limit, int offset) {
        /** 兼容缺省 offset 的 2 参构造：offset 默认 0。 */
        public SearchQuery(String keyword, int limit) {
            this(keyword, limit, 0);
        }
    }

    /**
     * 一条候选。
     *
     * @param id        实例 id（字符串，兼容雪花精度）
     * @param display   最终展示串（已按实体 {@link RenderMode} 渲染）
     * @param primary   主字段值（下拉主行，可空）
     * @param secondary 次字段值（下拉淡色辅助信息，无次字段时为 null）
     */
    record NameHit(String id, String display, String primary, String secondary) {}
}
