package com.wensaas.core.api.spi.def;

/**
 * 工作台业务数据卡片的占位宽度。
 *
 * <p>前端业务数据卡片区按此做多列瀑布式布局：
 * {@link #THIRD}=1/3、{@link #HALF}=1/2、{@link #FULL}=满行。
 */
public enum DashboardCardSpan {
    THIRD,
    HALF,
    FULL
}
