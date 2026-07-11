package com.wensaas.core.api.spi.def;

import java.util.List;

/**
 * 工作台业务数据卡片的运行时数据（由 {@link com.wensaas.core.api.spi.DashboardCardProvider#render()}
 * 在当前租户 + 当前用户上下文下计算返回）。
 *
 * <p>字段按 {@link DashboardCardDef#display()} 形态取用，其余可为 null/空：
 * <ul>
 *   <li>{@link DashboardCardDisplay#NUMBER} → {@code value}（+ {@code unit}）</li>
 *   <li>{@link DashboardCardDisplay#NUMBER_TREND} → {@code value} + {@code delta} + {@code deltaTone}</li>
 *   <li>{@link DashboardCardDisplay#LIST} → {@code items}</li>
 *   <li>{@link DashboardCardDisplay#CHART} → {@code trend}</li>
 * </ul>
 *
 * <p>全字段为空视为「无数据」，前端展示卡片空态。
 *
 * @param value     主数值 / 主文案（字符串以兼容金额、带单位等格式）
 * @param unit      数值单位（如「个」「天」，可空）
 * @param delta     同比 / 变化描述（如「+12% 同比上月」，可空）
 * @param deltaTone 变化色调：{@code up} / {@code down} / {@code danger} / {@code muted}（可空）
 * @param trend     近 N 期趋势序列（CHART 用，可空）
 * @param items     分项列表（LIST / 分布环用，可空）
 */
public record DashboardCardData(
        String value,
        String unit,
        String delta,
        String deltaTone,
        List<Long> trend,
        List<Item> items) {

    /**
     * 分项。
     *
     * @param label 分项标签
     * @param value 分项数值（字符串）
     * @param tone  色调（{@code danger} / {@code warning} / {@code success} / {@code muted}，可空）
     */
    public record Item(String label, String value, String tone) {
    }

    /** 纯数字卡片。 */
    public static DashboardCardData number(String value, String unit) {
        return new DashboardCardData(value, unit, null, null, null, null);
    }

    /** 数字 + 同比卡片。 */
    public static DashboardCardData numberTrend(String value, String unit, String delta, String deltaTone) {
        return new DashboardCardData(value, unit, delta, deltaTone, null, null);
    }

    /** 列表 / 分布卡片。 */
    public static DashboardCardData list(List<Item> items) {
        return new DashboardCardData(null, null, null, null, null, items);
    }

    /** 趋势图卡片。 */
    public static DashboardCardData chart(List<Long> trend) {
        return new DashboardCardData(null, null, null, null, trend, null);
    }
}
