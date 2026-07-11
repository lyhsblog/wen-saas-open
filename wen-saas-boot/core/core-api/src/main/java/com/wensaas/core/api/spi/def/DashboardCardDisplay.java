package com.wensaas.core.api.spi.def;

/**
 * 工作台业务数据卡片的展示形式。
 *
 * <p>前端按此渲染 {@link com.wensaas.core.api.spi.DashboardCardProvider} 返回的数据：
 * <ul>
 *   <li>{@link #NUMBER} —— 纯数字（取 {@code value} + {@code unit}）</li>
 *   <li>{@link #NUMBER_TREND} —— 数字 + 同比（取 {@code value} + {@code delta} + {@code deltaTone}）</li>
 *   <li>{@link #LIST} —— 小列表 / 分布（取 {@code items}）</li>
 *   <li>{@link #CHART} —— 简单柱状趋势（取 {@code trend}）</li>
 * </ul>
 */
public enum DashboardCardDisplay {
    NUMBER,
    NUMBER_TREND,
    LIST,
    CHART
}
