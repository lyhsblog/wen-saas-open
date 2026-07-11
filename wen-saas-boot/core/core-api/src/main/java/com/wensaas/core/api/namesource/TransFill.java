package com.wensaas.core.api.namesource;

/**
 * 响应 DTO「{@link TransField} 声明字段」的批量回填入口（api 门面）。
 *
 * <p>service 构造完响应后调一次 {@link #fill(Object)}：按 DTO 上的 {@link TransField} 声明
 * 反射收集 ref 值、按 type 分组批量取数、原地写回目标字段。根对象可为单个 DTO，也可直接传
 * {@code List} / {@code Map} / 数组（元素逐个处理，批量语义不变——同 type 聚合成一次取数）。
 *
 * <p>业务模块注入本接口即可使用，无需 import {@code core.impl.*}；默认实现由底座名称解析框架装配。
 * 嵌套对象须配合 {@link TransDescend}、目标字段类型与可变性约束见 {@link TransField} 文档。
 */
public interface TransFill {

    /**
     * 对 {@code root}（及其声明下钻的嵌套结构）执行一次声明式回填。
     * 翻不出的值保持目标字段原值，不抛异常；{@code root} 为 {@code null} 时静默返回。
     */
    void fill(Object root);
}
