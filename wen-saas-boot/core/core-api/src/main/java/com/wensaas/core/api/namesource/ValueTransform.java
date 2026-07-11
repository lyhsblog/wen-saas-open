package com.wensaas.core.api.namesource;

import java.util.Collection;
import java.util.Map;

/**
 * {@link TransField} 的<b>自定义逻辑转换</b> SPI（关注点：键值任意类型的派生）。
 *
 * <p>与 {@link NameLookup} 区分：{@code NameLookup}/{@code EntityNameResolver} 专做<b>表基础引用</b>
 * （{@code Long id → String 名}），形态固定、复用面广，不动；本接口面向<b>自定义逻辑转换</b>——键、值都可为
 * 任意类型（如 {@code code(String) → 名}、{@code code → 实体对象}、枚举→文案、复合拼装等）。{@code TransFiller}
 * 按 {@link TransField#type()} 分派：命中已注册的 {@code ValueTransform} 即走本接口（键值不做 {@code Long}
 * 强转、不假定值为 {@code String}）；否则回落 {@code EntityNameResolver} 表引用路径。
 *
 * <p>契约：
 * <ul>
 *   <li>{@link #type()} 与 {@link EntityTypes} / {@code NameLookup.entityType()} 取<b>同一命名空间</b>，
 *   全局唯一；同一 type 不得既注册 {@code ValueTransform} 又注册 {@code NameLookup}（启动期校验冲突）。</li>
 *   <li>{@link #transform} 返回 Map 中<b>不含</b>的键视为「翻不出」——{@code TransFiller} 保留目标原值
 *   （单值留原值、多值过滤该项），与 {@code NameLookup} 兜底约定一致。</li>
 *   <li>实现应只读 + 幂等；tenant 隔离自负，运营端跨租户须在
 *   {@code @TransField(scope = CROSS)} 上下文内（{@code TransFiller} 就地旁路租户线）。</li>
 * </ul>
 */
public interface ValueTransform {

    /** 处理的转换类型 code（与 {@link EntityTypes} / {@code NameLookup.entityType()} 同命名空间，全局唯一）。 */
    String type();

    /** 批量转换：给定一批键，返回 键 → 派生值。Map 中不出现的键表示翻不出（调用方保留原值兜底）。 */
    Map<Object, Object> transform(Collection<Object> keys);
}
