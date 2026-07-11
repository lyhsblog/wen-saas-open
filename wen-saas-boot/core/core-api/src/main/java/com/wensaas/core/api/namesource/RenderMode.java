package com.wensaas.core.api.namesource;

/**
 * 名称源默认实现（{@code LambdaNameSource}）的展示格式：决定展示串由几个字段组成，与具体字段语义无关。
 * 仅作用于底座 JDBC 默认源；业务自实现的 {@link NameLookup} 自决定格式。
 *
 * <p>{@link #render} 返回 {@code null} 表示「无可用名称」——调用方据 {@link NameLookup} 契约将其从结果
 * Map 中略去，让消费者按 id 兜底（而非把 id 硬拼进展示串）。
 *
 * <p>置于 api 层供业务模块经 {@code NameSourceFactory} 一行注册名称源时引用。
 */
public enum RenderMode {

    /** 仅主字段：{@code 主}。 */
    PRIMARY,
    /** 主带次字段：{@code 主(次)}；次为空退化为 {@code 主}。 */
    BOTH;

    /** 渲染展示串；主字段为空白时返回 {@code null}（无可用名称）。 */
    public String render(String primary, String secondary) {
        if (primary == null || primary.isBlank()) {
            return null;
        }
        if (this == PRIMARY || secondary == null || secondary.isBlank()) {
            return primary;
        }
        return primary + "(" + secondary + ")";
    }
}
