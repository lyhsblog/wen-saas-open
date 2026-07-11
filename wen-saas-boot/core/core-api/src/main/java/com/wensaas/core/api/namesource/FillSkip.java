package com.wensaas.core.api.namesource;

/**
 * {@link TransField#skipIf()} 的跳过模式：声明在派生字段上，回填前按「目标 / 来源当前状态」逐项判定，
 * <b>命中任意一项即跳过本次回填、保留目标原值</b>（OR 语义）。
 *
 * <p>不声明任何模式 = 从不跳过 = 始终回填（与历史行为一致）。「空」的口径：标量 {@code null} 算空、
 * 集合 / Map / 数组为空算空、空白字符串算空。
 */
public enum FillSkip {

    /** 目标字段当前 {@code != null}（含空串 / 空集合）→ 跳过，不覆盖已有值。 */
    TARGET_NOT_NULL,

    /** 目标字段当前有实际内容（非 null 且 非空串 / 非空集合）→ 跳过；空串 / 空集合仍会被回填。 */
    TARGET_NOT_BLANK,

    /** 来源 ref 为 {@code null} → 跳过。 */
    SOURCE_NULL,

    /** 来源为空（标量 null 或 集合空）→ 跳过。 */
    SOURCE_BLANK,

    /** 来源标量值为 {@code 0}（常见「无值」哨兵）→ 跳过；集合型来源不适用，恒不命中。 */
    SOURCE_ZERO
}
