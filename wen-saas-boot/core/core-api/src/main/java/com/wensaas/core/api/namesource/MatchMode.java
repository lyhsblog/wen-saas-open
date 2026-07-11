package com.wensaas.core.api.namesource;

/**
 * 名称源默认实现（{@code LambdaNameSource}）中单个可搜字段的反向匹配模式。
 *
 * <p>每个字段在注册时声明自己的匹配模式：名称类用 {@link #PREFIX}（输入前缀即命中），编号 / code 类用
 * {@link #EXACT}（整串相等才命中）。单关键字搜索时各字段按自己的模式 OR 命中。
 */
public enum MatchMode {

    /** 前缀匹配（{@code likeRight}，{@code 输入%}）。名称类字段用。 */
    PREFIX,
    /** 精确匹配（{@code eq}）。编号 / code 类字段用。 */
    EXACT
}
