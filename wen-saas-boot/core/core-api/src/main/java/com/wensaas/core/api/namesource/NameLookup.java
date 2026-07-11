package com.wensaas.core.api.namesource;

import java.util.Collection;
import java.util.Map;

/**
 * 实体「id → 展示名」翻译 SPI。
 *
 * <p>给定一个实体类型 + 一批 id，批量产出展示名。被审计渲染、列表/导出外键列、自定义字段 REF、下拉等
 * 多处复用，避免每个模块各写一套 {@code selectBatchIds + Map} 翻译逻辑。
 *
 * <p>底座对 id/code/name 列规整的实体提供默认实现
 * {@code com.wensaas.core.impl.namesource.LambdaNameSource}（基于实体类 + 方法引用，一行注册即可）；
 * 需 join / 特殊脱敏 / 跨库的业务实体直接实现本接口、注入业务 Service 自查。
 *
 * <p>契约：
 * <ul>
 *   <li>{@link #entityType()} 全局唯一；同一 entityType 只能注册一个实现（启动期校验，重复抛
 *   {@link IllegalStateException}）。与自定义字段 {@code CustomFieldObjectDef.code()} 取同一命名空间</li>
 *   <li>{@link #batchLookup} 返回 Map 中<b>不包含</b>的 id 视为「无法翻译」（已删除 / 无名），
 *   调用方按 id 原样兜底展示</li>
 *   <li>实现应只读 + 幂等；返回的已是<b>最终展示串</b>（业务自决定 {@code 名称(code)} 等格式，框架不再加工）</li>
 *   <li>tenant 隔离由实现内部负责；运营端跨租户解析须在 {@code @CrossTenantQuery} 上下文内调用</li>
 * </ul>
 */
public interface NameLookup {

    /** 处理的实体类型 code（如 {@code "sys.tenant"} / {@code "crm.customer"}）。 */
    String entityType();

    /**
     * 实体类型的中文显示名（type-level，如「客户」「角色」）。
     *
     * <p>注意与「实例名」区分：这是<b>类型</b>的标签（一个类型一个），而 {@link #batchLookup} 返回的是
     * <b>某一行实例</b>的名字（如「张三」）。类型 label 统一以本方法为权威单一来源——自定义字段宿主
     * 选择项、审计「对象类型」下拉等处统一引用，不再各自声明（参见 {@code CustomFieldObjectDef}）。
     */
    String displayLabel();

    /** 批量翻译，返回 id → 最终展示串。Map 中不出现的 id 表示无法翻译（调用方按 id 兜底）。 */
    Map<Long, String> batchLookup(Collection<Long> ids);
}
